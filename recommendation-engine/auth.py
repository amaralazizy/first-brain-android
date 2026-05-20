# AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
# See README §12 for the team's originality statement.

"""
JWT verification for the First Brain recommendation server.

Tokens are issued by Neon Auth (Better Auth) and signed with Ed25519. The
public keys are fetched from the project's JWKS endpoint and cached in
memory. Verification follows the standard OIDC flow:

    1. Read `Authorization: Bearer <jwt>` off the incoming request.
    2. Pull the `kid` from the JWT header.
    3. Look up the matching JWK (refresh once if missing — handles rotation).
    4. Verify signature, `iss`, and `exp`.
    5. Return the `sub` claim as the authenticated user id.

The audience claim is **not** enforced because Neon issues it equal to the
issuer, which doesn't add a meaningful check; `iss` already pins us to the
right project.
"""

from __future__ import annotations

import logging
import os
import threading
import time
from typing import Any

import httpx
import jwt
from fastapi import Depends, HTTPException, Request, status

logger = logging.getLogger("first-brain.auth")

NEON_AUTH_ISSUER = os.environ.get(
    "NEON_AUTH_ISSUER",
    "https://ep-proud-resonance-amlj3hjf.neonauth.c-5.us-east-1.aws.neon.tech",
)
JWKS_URL = f"{NEON_AUTH_ISSUER}/neondb/auth/.well-known/jwks.json"

# JWKS in-memory cache. Refresh on `kid` miss (rotation) and every 10 min anyway.
_JWKS_TTL_SECONDS = 600
_jwks_lock = threading.Lock()
_jwks_cache: dict[str, Any] = {"keys_by_kid": {}, "fetched_at": 0.0}


def _fetch_jwks(force: bool = False) -> dict[str, jwt.PyJWK]:
    with _jwks_lock:
        now = time.time()
        fresh_enough = (now - _jwks_cache["fetched_at"]) < _JWKS_TTL_SECONDS
        if _jwks_cache["keys_by_kid"] and fresh_enough and not force:
            return _jwks_cache["keys_by_kid"]

        logger.info("Fetching JWKS from %s", JWKS_URL)
        resp = httpx.get(JWKS_URL, timeout=5.0)
        resp.raise_for_status()
        jwks = resp.json()
        keys_by_kid = {k["kid"]: jwt.PyJWK(k) for k in jwks.get("keys", [])}
        _jwks_cache["keys_by_kid"] = keys_by_kid
        _jwks_cache["fetched_at"] = now
        return keys_by_kid


def _key_for(kid: str) -> jwt.PyJWK:
    keys = _fetch_jwks()
    pyjwk = keys.get(kid)
    if pyjwk is None:
        # Possible rotation — refresh once before giving up.
        keys = _fetch_jwks(force=True)
        pyjwk = keys.get(kid)
    if pyjwk is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Unknown signing key: {kid}",
        )
    return pyjwk


def verify_jwt(token: str) -> dict[str, Any]:
    try:
        headers = jwt.get_unverified_header(token)
    except jwt.PyJWTError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail=f"Malformed token: {e}"
        )

    kid = headers.get("kid")
    if not kid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing `kid` header"
        )

    pyjwk = _key_for(kid)
    try:
        claims = jwt.decode(
            token,
            key=pyjwk.key,
            algorithms=[headers.get("alg", "EdDSA")],
            issuer=NEON_AUTH_ISSUER,
            options={"verify_aud": False},
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
    except jwt.PyJWTError as e:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=f"Invalid token: {e}")

    if "sub" not in claims:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing `sub` claim")
    return claims


def get_current_user(request: Request) -> str:
    """FastAPI dependency — returns the authenticated user id (`sub` claim)."""
    header = request.headers.get("Authorization", "")
    if not header.lower().startswith("bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing Bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token = header[len("Bearer "):].strip()
    claims = verify_jwt(token)
    return claims["sub"]


CurrentUser = Depends(get_current_user)