package br.com.carpark.glpihub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.carpark.glpihub.data.DataStoreManager
import br.com.carpark.glpihub.data.GlpiScraper
import br.com.carpark.glpihub.presentation.LoginScreen
import br.com.carpark.glpihub.presentation.MainScreen
import br.com.carpark.glpihub.presentation.MainViewModel
import br.com.carpark.glpihub.ui.theme.GLPIHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialização manual das dependências (substituindo Dagger/Hilt para simplicidade)
        val dataStoreManager = DataStoreManager(applicationContext)
        val scraper = GlpiScraper()

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(scraper, dataStoreManager) as T
            }
        }
        val viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        viewModel.checkLoginStatus()

        setContent {
            val currentTheme by viewModel.themeType.collectAsState()
            GLPIHubTheme(themeType = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // Mostra a SplashScreen enquanto decide o estado de login
    if (isLoggedIn == null) {
        br.com.carpark.glpihub.presentation.SplashScreen()
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn == true) "main" else "login"
    ) {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                onTicketClick = { ticketId ->
                    navController.navigate("ticketDetails/$ticketId")
                }
            )
        }
        composable(
            route = "ticketDetails/{ticketId}",
            arguments = listOf(androidx.navigation.navArgument("ticketId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId") ?: return@composable
            br.com.carpark.glpihub.presentation.TicketDetailsScreen(
                ticketId = ticketId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
