$urlLogin = "http://acesso.carpark.com.br:8080/index.php?noAUTO=1"
$urlPostLogin = "http://acesso.carpark.com.br:8080/front/login.php"

$resInit = Invoke-WebRequest -Uri $urlLogin -SessionVariable glpiSession -UseBasicParsing
$html = $resInit.Content

$csrf = ""
if ($html -match 'name="_glpi_csrf_token" value="(.*?)"') {
    $csrf = $matches[1]
}

$loginData = @{
    login_name = "rodrigo.santos"
    login_password = "T9z8D6e."
    _glpi_csrf_token = $csrf
    noAUTO = "1"
    submit = "Enviar"
}

$resLogin = Invoke-WebRequest -Uri $urlPostLogin -WebSession $glpiSession -Method Post -Body $loginData -UseBasicParsing

$urlTicket = "http://acesso.carpark.com.br:8080/front/ticket.form.php?id=21125"
$resTicket = Invoke-WebRequest -Uri $urlTicket -WebSession $glpiSession -UseBasicParsing
$ticketHtml = $resTicket.Content

$ticketCsrf = ""
if ($ticketHtml -match 'name="_glpi_csrf_token" value="(.*?)"') {
    $ticketCsrf = $matches[1]
}

$followupData = @{
    itemtype = "Ticket"
    items_id = "21125"
    content = "Testando acompanhamento."
    add = "Adicionar"
    _glpi_csrf_token = $ticketCsrf
}

$urlFollowup = "http://acesso.carpark.com.br:8080/front/itilfollowup.form.php"
$resFollowup = Invoke-WebRequest -Uri $urlFollowup -WebSession $glpiSession -Method Post -Body $followupData -UseBasicParsing

Write-Output "Status da resposta: $($resFollowup.StatusCode)"
