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
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
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

        // Set up Bottom Navigation
        findViewById<BottomNavigationView>(R.id.bottomNav)
            .setupWithNavController(navController)

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

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Invalidate the options menu whenever the destination changes (so onPrepareOptionsMenu runs)
        navController.addOnDestinationChangedListener { _, _, _ -> invalidateOptionsMenu() }
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