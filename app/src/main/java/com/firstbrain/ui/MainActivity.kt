package com.firstbrain.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.firstbrain.R
import com.firstbrain.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        val topLevel = setOf(
            R.id.todaysPicksFragment,
            R.id.tasksFragment,
            R.id.analyticsFragment,
            R.id.historyFragment,
            R.id.insightsFragment,
        )
        val appBarConfig = AppBarConfiguration(topLevel)
        binding.toolbar.setupWithNavController(navController, appBarConfig)
        binding.bottomNav.setupWithNavController(navController)
    }
}
