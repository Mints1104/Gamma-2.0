package com.mints.projectgammatwo.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mints.projectgammatwo.R
import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set up Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Configure AppBar
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.filterFragment,
                R.id.settingsFragment,
                R.id.favoritesFragment,
                R.id.questsFragment
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Set up Bottom Navigation with custom handling to avoid history back stack issues
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            val destinationId = item.itemId
            if (navController.currentDestination?.id == destinationId) return@setOnItemSelectedListener true

            // Always clear the entire back stack when switching bottom tabs
            val opts = navOptions {
                launchSingleTop = true
                restoreState = false
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
            }
            return@setOnItemSelectedListener runCatching {
                navController.navigate(destinationId, null, opts)
                true
            }.getOrDefault(false)
        }

        // Update bottom nav selection based on destination; clear highlight for non-bottom destinations
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val menu = bottomNav.menu
            when (destination.id) {
                R.id.deletedInvasionsFragment -> {
                    // Highlight Rockets/Home without triggering navigation
                    menu.findItem(R.id.homeFragment)?.isChecked = true
                }
                R.id.deletedQuestsFragment -> {
                    // Highlight Quests without triggering navigation
                    menu.findItem(R.id.questsFragment)?.isChecked = true
                }
                R.id.homeFragment, R.id.filterFragment, R.id.settingsFragment, R.id.favoritesFragment, R.id.questsFragment -> {
                    menu.findItem(destination.id)?.isChecked = true
                }
                else -> {
                    // Leave current check state as-is for non-bottom destinations
                }
            }
            // Invalidate the options menu whenever the destination changes
            invalidateOptionsMenu()
        }

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate both menus once; we'll toggle visibility in onPrepare
        menuInflater.inflate(R.menu.rockets_nav_menu, menu)
        menuInflater.inflate(R.menu.quests_nav_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val dest = navController.currentDestination?.id
        val isHome = dest == R.id.homeFragment
        val isQuests = dest == R.id.questsFragment
        val inHistory = dest == R.id.deletedInvasionsFragment || dest == R.id.deletedQuestsFragment

        // Show History only on Home, hide otherwise and always hide while in history screens
        menu.findItem(R.id.action_open_history)?.isVisible = isHome && !inHistory
        // Show Visited Quests only on Quests, hide otherwise and always hide while in history screens
        menu.findItem(R.id.action_open_visited_quests)?.isVisible = isQuests && !inHistory
        // Show sort options only on Home
        menu.findItem(R.id.action_sort_by_time)?.isVisible = isHome
        menu.findItem(R.id.action_sort_by_distance)?.isVisible = isHome
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_history -> {
                if (navController.currentDestination?.id != R.id.deletedInvasionsFragment) {
                    runCatching { navController.navigate(R.id.deletedInvasionsFragment) }
                }
                return true
            }
            R.id.action_open_visited_quests -> {
                if (navController.currentDestination?.id != R.id.deletedQuestsFragment) {
                    runCatching { navController.navigate(R.id.deletedQuestsFragment) }
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}