import org.jsoup.Jsoup
import org.jsoup.Connection

fun main() {
    val baseUrl = "http://acesso.carpark.com.br:8080"
    try {
        val resInit = Jsoup.connect("$baseUrl/front/central.php").execute()
        val doc = resInit.parse()
        val csrfToken = doc.select("input[name=_glpi_csrf_token]").first()?.attr("value") ?: ""
        val loginNameAttr = doc.select("input#login_name").first()?.attr("name") ?: "login_name"
        val loginPassAttr = doc.select("input#login_password").first()?.attr("name") ?: "login_password"
        val rememberNameAttr = doc.select("input#login_remember").first()?.attr("name") ?: "remember"

        println("Init OK. CSRF: $csrfToken")

        val resLogin = Jsoup.connect("$baseUrl/front/login.php")
            .data("_glpi_csrf_token", csrfToken)
            .data("redirect", "/front/central.php")
            .data("auth", "ldap-1")
            .data("submit", "Enviar")
            .data(loginNameAttr, "rodrigo.santos")
            .data(loginPassAttr, "T9z8D6e.")
            .data(rememberNameAttr, "on")
            .cookies(resInit.cookies())
            .method(Connection.Method.POST)
            .followRedirects(true)
            .execute()

        println("Final URL: " + resLogin.url().toString())
    } catch (e: Exception) {
        println("Error: " + e.message)
    }
}
