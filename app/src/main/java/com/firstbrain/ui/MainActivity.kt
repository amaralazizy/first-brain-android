package com.firstbrain.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkManager
import com.firstbrain.R
import com.firstbrain.data.auth.AuthRepository
import com.firstbrain.data.auth.AuthState
import com.firstbrain.databinding.ActivityMainBinding
import com.firstbrain.worker.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var workManager: WorkManager

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val authDestinations = setOf(R.id.loginFragment, R.id.signUpFragment)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        askNotificationPermission()
        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        val topLevel = setOf(
            R.id.todaysPicksFragment,
            R.id.tasksFragment,
            R.id.analyticsFragment,
            R.id.historyFragment,
        )
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(topLevel))
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val authScreen = destination.id in authDestinations
            binding.toolbar.visibility = if (authScreen) View.GONE else View.VISIBLE
            binding.bottomNav.visibility = if (authScreen) View.GONE else View.VISIBLE
            invalidateOptionsMenu()
        }

        if (authRepository.state.value is AuthState.Authenticated) {
            navController.navigate(R.id.action_auth_to_home)
            SyncWorker.enqueueNow(workManager)
            SyncWorker.schedulePeriodic(workManager)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepository.state.collect { state ->
                    if (state is AuthState.Unauthenticated &&
                        navController.currentDestination?.id !in authDestinations
                    ) {
                        navController.navigate(
                            R.id.loginFragment,
                            null,
                            NavOptions.Builder()
                                .setPopUpTo(R.id.nav_graph, true)
                                .build(),
                        )
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val authScreen = navController.currentDestination?.id in authDestinations
        menu.findItem(R.id.action_sign_out)?.isVisible = !authScreen
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                lifecycleScope.launch { authRepository.signOut() }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
