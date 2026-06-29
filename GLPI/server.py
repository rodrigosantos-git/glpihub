import os
import sys
import secrets
import json
import base64
from flask import Flask, request, jsonify, send_from_directory, session
import requests
from bs4 import BeautifulSoup
from datetime import datetime
import re

try:
    import winreg
    import win32crypt
    HAS_WINDOWS = True
except ImportError:
    HAS_WINDOWS = False

# Caminho de fallback de configurações locais para plataformas não-Windows (como Linux)
FALLBACK_CONFIG_PATH = os.path.expanduser("~/.glpi_hub_settings.json")
REG_PATH = r"Software\GLPI_HUB"

def set_reg_value(name, value, value_type=None):
    """ Grava um valor no Registro do Windows sob HKCU """
    if value_type is None and HAS_WINDOWS:
        value_type = winreg.REG_SZ
    try:
        key = winreg.CreateKey(winreg.HKEY_CURRENT_USER, REG_PATH)
        winreg.SetValueEx(key, name, 0, value_type, value)
        winreg.CloseKey(key)
        return True
    except Exception as e:
        print(f"Erro ao gravar no Registro: {e}")
        return False

def get_reg_value(name, default=None):
    """ Lê um valor do Registro do Windows sob HKCU """
    try:
        key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, REG_PATH, 0, winreg.KEY_READ)
        value, _ = winreg.QueryValueEx(key, name)
        winreg.CloseKey(key)
        return value
    except Exception:
        return default

def get_fallback_settings():
    """ Carrega as configurações locais em JSON do arquivo de fallback """
    if os.path.exists(FALLBACK_CONFIG_PATH):
        try:
            with open(FALLBACK_CONFIG_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}

def save_fallback_settings(settings):
    """ Salva as configurações mescladas no arquivo JSON de fallback """
    try:
        existing = get_fallback_settings()
        existing.update(settings)
        with open(FALLBACK_CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(existing, f, indent=4)
        return True
    except Exception as e:
        print(f"Erro ao salvar fallback config: {e}")
        return False

def encrypt_password(password_str):
    """ Criptografa a senha usando DPAPI no Windows ou fallback base64 em outras plataformas """
    if not password_str:
        return ""
    if HAS_WINDOWS:
        try:
            encrypted_bytes = win32crypt.CryptProtectData(password_str.encode('utf-8'), None, None, None, None, 0)
            return base64.b64encode(encrypted_bytes).decode('utf-8')
        except Exception as e:
            print(f"Erro ao criptografar senha com DPAPI: {e}")
            return ""
    else:
        # Fallback para criptografia/ofuscação base64 se não estiver no Windows
        return "fb:" + base64.b64encode(password_str.encode('utf-8')).decode('utf-8')

def decrypt_password(encrypted_str):
    """ Descriptografa a senha usando DPAPI no Windows ou fallback base64 em outras plataformas """
    if not encrypted_str:
        return ""
    if HAS_WINDOWS:
        try:
            encrypted_bytes = base64.b64decode(encrypted_str.encode('utf-8'))
            decrypted_bytes = win32crypt.CryptUnprotectData(encrypted_bytes, None, None, None, 0)
            return decrypted_bytes[1].decode('utf-8')
        except Exception as e:
            print(f"Erro ao descriptografar senha com DPAPI: {e}")
            return ""
    else:
        if encrypted_str.startswith("fb:"):
            try:
                base64_str = encrypted_str[3:]
                return base64.b64decode(base64_str.encode('utf-8')).decode('utf-8')
            except Exception:
                return ""
        return ""

def get_resources_path(relative_path):
    """ Retorna o caminho absoluto do recurso, compatível com execução local e PyInstaller """
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath(os.path.dirname(__file__)), relative_path)

static_dir = get_resources_path("static")

app = Flask(__name__, static_folder=static_dir)
# Chave secreta criptograficamente segura para gerenciamento de sessão Flask
app.secret_key = secrets.token_hex(32)

# Configurações do portal GLPI
GLPI_BASE_URL = "http://acesso.carpark.com.br:8080"
GLPI_CENTRAL_URL = f"{GLPI_BASE_URL}/front/central.php"
GLPI_LOGIN_URL = f"{GLPI_BASE_URL}/front/login.php"
GLPI_TICKET_URL = f"{GLPI_BASE_URL}/front/ticket.php"

# Dicionário em memória para salvar as sessões ativas do requests (Segurança contra roubo de tokens)
active_sessions = {}

def parse_glpi_date(date_str):
    """
    Normaliza e analisa a data retornada pelo GLPI.
    Diferentes formatos são testados para ordenar os chamados de forma precisa.
    Retorna datetime.min caso não seja analisável.
    """
    if not date_str:
        return datetime.min
    
    date_str = date_str.strip()
    date_str = re.sub(r'\s+', ' ', date_str)
    
    formats = [
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%d-%m-%Y %H:%M:%S",
        "%d-%m-%Y %H:%M",
        "%d/%m/%Y %H:%M:%S",
        "%d/%m/%Y %H:%M"
    ]
    
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue
            
    # Fallback via Regex se a data possuir formato alternativo
    try:
        match = re.search(r'(\d{2})[-/](\d{2})[-/](\d{4})\s*(\d{2}):(\d{2})', date_str)
        if match:
            d, m, y, h, mn = match.groups()
            return datetime(int(y), int(m), int(d), int(h), int(mn))
    except Exception:
        pass
        
    return datetime.min

def simplify_technician_name(tech_name):
    """
    Normaliza o nome do técnico atribuído do GLPI.
    Filtra tags de email, cargos e encurta para "Primeiro + Último Nome" com capitalização correta.
    """
    if not tech_name or tech_name.strip() == "" or "não atribuído" in tech_name.lower():
        return "Não Atribuído"
        
    # Remove prefixos comuns no GLPI
    name = re.sub(r'^(técnico(s)?|tech|responsável)\s*:\s*', '', tech_name, flags=re.IGNORECASE)
    
    # Remove e-mails e parênteses
    name = re.sub(r'[\(\[\{].*?[\)\]\}]', '', name)
    name = re.sub(r'\S+@\S+', '', name)
    name = re.sub(r'^\s*[-\—\–]\s*|\s*[-\—\–]\s*$', '', name)
    
    name = re.sub(r'\s+', ' ', name).strip()
    
    parts = name.split()
    parts = [p.capitalize() for p in parts]
    if len(parts) > 2:
        return f"{parts[0]} {parts[-1]}"
    elif len(parts) == 0:
        return "Não Atribuído"
        
    return " ".join(parts)

def extract_tickets_html(html_content):
    """
    Analisa o HTML do GLPI e extrai os chamados em um mapeamento dinâmico de colunas.
    Dessa forma, alterações no layout de colunas no perfil do GLPI não quebram o parser.
    """
    soup = BeautifulSoup(html_content, "html.parser")
    tickets = []
    
    # Busca a tabela principal de chamados no GLPI
    table = soup.find("table", class_="tab_cadre_fixehover")
    if not table:
        table = soup.find("table", class_="tab_cadre_fixe")
    if not table:
        tables = soup.find_all("table")
        for t in tables:
            if t.find("th") or t.find("td", class_="tab_bg_1"):
                table = t
                break
                
    if not table:
        return []

    rows = table.find_all("tr")
    if not rows:
        return []
        
    headers = []
    header_row = rows[0]
    
    # Extrai os cabeçalhos para mapeamento dinâmico
    th_elements = header_row.find_all(["th", "td"])
    for idx, th in enumerate(th_elements):
        text = th.get_text().strip().lower()
        headers.append(text)
        
    # Inicializa índices
    id_idx = -1
    title_idx = -1
    description_idx = -1
    date_idx = -1
    category_idx = -1
    requester_idx = -1
    tech_idx = -1
    status_idx = -1
    priority_idx = -1
    entity_idx = -1
    
    # Mapeamento dinâmico baseado na tradução e termos do GLPI
    for i, h in enumerate(headers):
        if h == "id" or h == "código" or h == "code":
            id_idx = i
        elif "título" in h or "title" in h or "assunto" in h or "requisição" in h:
            title_idx = i
        elif "descrição" in h or "description" in h or "conteúdo" in h or "content" in h:
            description_idx = i
        elif "abertura" in h or "data" in h or "criado" in h or "date" in h:
            date_idx = i
        elif "categoria" in h or "category" in h:
            category_idx = i
        elif "requerente" in h or "solicitante" in h or "requester" in h or "autor" in h:
            requester_idx = i
        elif "técnico" in h or "atribuído" in h or "assigned" in h or "responsável" in h:
            tech_idx = i
        elif "status" in h or "estado" in h:
            status_idx = i
        elif "prioridade" in h or "priority" in h:
            priority_idx = i
        elif "entidade" in h or "entity" in h:
            entity_idx = i

    # Iteração sobre as linhas da tabela de chamados (ignora a primeira linha de headers)
    for row in rows[1:]:
        if row.find("input", {"type": "submit"}) or "paginação" in row.get_text().lower() or "page" in row.get_text().lower():
            continue
            
        cells = row.find_all("td")
        if len(cells) < 4:
            continue
            
        def get_cell_text(idx):
            if 0 <= idx < len(cells):
                return cells[idx].get_text(separator=" ").strip()
            return ""
            
        ticket_id = get_cell_text(id_idx) if id_idx != -1 else ""
        date_str = get_cell_text(date_idx) if date_idx != -1 else ""
        category = get_cell_text(category_idx) if category_idx != -1 else ""
        requester = get_cell_text(requester_idx) if requester_idx != -1 else ""
        tech = get_cell_text(tech_idx) if tech_idx != -1 else ""
        status = get_cell_text(status_idx) if status_idx != -1 else ""
        priority = get_cell_text(priority_idx) if priority_idx != -1 else ""
        entity = get_cell_text(entity_idx) if entity_idx != -1 else ""
        
        # Parse de título e descrição de dentro da célula de título (ou coluna dedicada)
        title = ""
        description = ""
        title_cell = cells[title_idx] if title_idx != -1 else None
        if title_cell:
            a_link = title_cell.find("a")
            if a_link:
                title = a_link.get_text().strip()
                cell_txt = title_cell.get_text(separator=" ").strip()
                description = cell_txt.replace(title, "", 1).strip()
                description = re.sub(r'^[\s\-\—\:\,\.\/]+', '', description)
            else:
                title = title_cell.get_text().strip()
                
        if description_idx != -1:
            col_desc = get_cell_text(description_idx)
            if col_desc:
                description = col_desc
                
        description = re.sub(r'\s+', ' ', description).strip()
        if not description:
            description = "Sem descrição detalhada."
            
        if not ticket_id:
            # Fallback para capturar ID da primeira coluna numérica curta
            for cell in cells:
                val = cell.get_text().strip()
                if val.isdigit() and 1 <= len(val) <= 7:
                    ticket_id = val
                    break
        if not title:
            link = row.find("a", href=re.compile(r"ticket\.form\.php"))
            if link:
                title = link.get_text().strip()
                if not ticket_id:
                    match_id = re.search(r"id=(\d+)", link['href'])
                    if match_id:
                        ticket_id = match_id.group(1)
        
        if not ticket_id and not title:
            continue
            
        ticket_id = re.sub(r'\D', '', ticket_id)
        
        tech_clean = simplify_technician_name(tech)
        requester_clean = re.sub(r'\s+', ' ', requester).strip()
        entity_clean = re.sub(r'\s+', ' ', entity).strip() if entity else "Geral"
        category_clean = re.sub(r'\s+', ' ', category).strip() if category else "Sem Categoria"
        
        ticket_url = f"{GLPI_BASE_URL}/front/ticket.form.php?id={ticket_id}"
        
        tickets.append({
            "id": ticket_id,
            "title": title,
            "description": description,
            "date": date_str,
            "parsed_date": parse_glpi_date(date_str),
            "category": category_clean,
            "entity": entity_clean,
            "requester": requester_clean,
            "technician": tech_clean,
            "status": status if status else "Novo",
            "priority": priority if priority else "Média",
            "url": ticket_url
        })
        
    return tickets

# Rotas Estáticas para Hospedar a SPA
@app.route("/")
def serve_index():
    return send_from_directory(static_dir, "index.html")

@app.route("/<path:path>")
def serve_static(path):
    return send_from_directory(static_dir, path)

# Endpoints da API REST
@app.route("/api/auth/status", methods=["GET"])
def auth_status():
    token = session.get("local_token")
    if token and token in active_sessions:
        return jsonify({"authenticated": True})
    return jsonify({"authenticated": False})

@app.route("/api/auth/login", methods=["POST"])
def auth_login():
    data = request.json or {}
    username = data.get("username")
    password = data.get("password")
    auth_method = data.get("auth_method", "ldap-1")
    
    if not username or not password:
        return jsonify({"error": "Usuário e senha são obrigatórios."}), 400
        
    scrape_session = requests.Session()
    
    try:
        # Carrega a página inicial de login do GLPI para capturar o cookie de sessão e o Token CSRF
        res = scrape_session.get(GLPI_CENTRAL_URL, timeout=15)
        res.raise_for_status()
        
        soup = BeautifulSoup(res.text, "html.parser")
        csrf_input = soup.find("input", {"name": "_glpi_csrf_token"})
        if not csrf_input:
            return jsonify({"error": "Não foi possível localizar o token CSRF de segurança na página do GLPI."}), 500
        csrf_token = csrf_input["value"]
        
        user_input = soup.find("input", {"id": "login_name"})
        pass_input = soup.find("input", {"id": "login_password"})
        remember_input = soup.find("input", {"id": "login_remember"})
        
        if not user_input or not pass_input:
            return jsonify({"error": "Interface de formulário do GLPI modificada ou inacessível."}), 500
            
        user_field = user_input["name"]
        pass_field = pass_input["name"]
        remember_field = remember_input["name"] if remember_input else "remember"
        
        payload = {
            "_glpi_csrf_token": csrf_token,
            "redirect": "/front/central.php",
            "auth": auth_method,
            "submit": "Enviar",
            user_field: username,
            pass_field: password,
            remember_field: "on"
        }
        
        login_res = scrape_session.post(GLPI_LOGIN_URL, data=payload, timeout=15)
        
        if "Uso inválido de ID de sessão" in login_res.text or "error" in login_res.url or "login" in login_res.url:
            soup_err = BeautifulSoup(login_res.text, "html.parser")
            err_div = soup_err.find("div", class_="error")
            err_msg = err_div.get_text().strip() if err_div else "Usuário ou senha incorretos no portal GLPI."
            return jsonify({"error": err_msg}), 401
            
        # Cria um token de sessão local e salva na sessão criptografada do Flask
        local_token = secrets.token_urlsafe(32)
        active_sessions[local_token] = scrape_session
        session["local_token"] = local_token
        
        # Salva ou limpa credenciais se lembrar_senha estiver ativo/inativo
        remember_pwd = data.get("remember_password", False)
        if remember_pwd:
            enc_pwd = encrypt_password(password)
            if HAS_WINDOWS:
                set_reg_value("remember_password", "1")
                set_reg_value("saved_username", username)
                set_reg_value("saved_password", enc_pwd)
                set_reg_value("saved_domain", auth_method)
            else:
                save_fallback_settings({
                    "remember_password": "1",
                    "saved_username": username,
                    "saved_password": enc_pwd,
                    "saved_domain": auth_method
                })
        else:
            if HAS_WINDOWS:
                set_reg_value("remember_password", "0")
                set_reg_value("saved_username", "")
                set_reg_value("saved_password", "")
            else:
                save_fallback_settings({
                    "remember_password": "0",
                    "saved_username": "",
                    "saved_password": ""
                })
        
        return jsonify({"success": True, "message": "Autenticação realizada com sucesso."})
        
    except requests.RequestException as e:
        return jsonify({"error": f"Erro de comunicação de rede com o GLPI: {str(e)}"}), 502
    except Exception as e:
        return jsonify({"error": f"Erro interno do backend: {str(e)}"}), 500

@app.route("/api/settings", methods=["GET"])
def get_settings_endpoint():
    if HAS_WINDOWS:
        refresh_rate = get_reg_value("refresh_rate", "0")
        layout_view = get_reg_value("layout_view", "grid")
        theme = get_reg_value("theme", "light")
        notifications_mode = get_reg_value("notifications_mode", "all")
        remember_password = get_reg_value("remember_password", "0")
        saved_username = get_reg_value("saved_username", "")
        saved_password_enc = get_reg_value("saved_password", "")
        saved_domain = get_reg_value("saved_domain", "ldap-1")
    else:
        cfg = get_fallback_settings()
        refresh_rate = str(cfg.get("refresh_rate", "0"))
        layout_view = cfg.get("layout_view", "grid")
        theme = cfg.get("theme", "light")
        notifications_mode = cfg.get("notifications_mode", "all")
        remember_password = str(cfg.get("remember_password", "0"))
        saved_username = cfg.get("saved_username", "")
        saved_password_enc = cfg.get("saved_password", "")
        saved_domain = cfg.get("saved_domain", "ldap-1")

    saved_password = ""
    if remember_password == "1" and saved_password_enc:
        saved_password = decrypt_password(saved_password_enc)

    return jsonify({
        "refresh_rate": int(refresh_rate) if refresh_rate.isdigit() else 0,
        "layout_view": layout_view,
        "theme": theme,
        "notifications_mode": notifications_mode,
        "remember_password": remember_password == "1",
        "saved_username": saved_username,
        "saved_password": saved_password,
        "saved_domain": saved_domain
    })

@app.route("/api/settings", methods=["POST"])
def save_settings_endpoint():
    data = request.json or {}
    
    refresh_rate = str(data.get("refresh_rate", 0))
    layout_view = data.get("layout_view", "grid")
    theme = data.get("theme", "light")
    notifications_mode = data.get("notifications_mode", "all")
    remember_password = "1" if data.get("remember_password", False) else "0"
    saved_username = data.get("saved_username", "")
    saved_password_plain = data.get("saved_password", "")
    saved_domain = data.get("saved_domain", "ldap-1")
    
    if remember_password == "1" and saved_password_plain:
        saved_password_enc = encrypt_password(saved_password_plain)
    else:
        saved_password_enc = ""
        if remember_password == "0":
            saved_username = ""
            
    if HAS_WINDOWS:
        import winreg
        set_reg_value("refresh_rate", refresh_rate, winreg.REG_SZ)
        set_reg_value("layout_view", layout_view, winreg.REG_SZ)
        set_reg_value("theme", theme, winreg.REG_SZ)
        set_reg_value("notifications_mode", notifications_mode, winreg.REG_SZ)
        set_reg_value("remember_password", remember_password, winreg.REG_SZ)
        set_reg_value("saved_username", saved_username, winreg.REG_SZ)
        set_reg_value("saved_password", saved_password_enc, winreg.REG_SZ)
        set_reg_value("saved_domain", saved_domain, winreg.REG_SZ)
    else:
        save_fallback_settings({
            "refresh_rate": refresh_rate,
            "layout_view": layout_view,
            "theme": theme,
            "notifications_mode": notifications_mode,
            "remember_password": remember_password,
            "saved_username": saved_username,
            "saved_password": saved_password_enc,
            "saved_domain": saved_domain
        })
        
    return jsonify({"success": True})

@app.route("/api/tickets", methods=["GET"])
def get_tickets():
    token = session.get("local_token")
    if not token or token not in active_sessions:
        return jsonify({"error": "Sessão expirada ou não autenticada."}), 401
        
    scrape_session = active_sessions[token]
    
    try:
        # Executa a busca em ticket.php com limite de 1000 chamados recentes
        params = {
            "is_deleted": 0,
            "start": 0,
            "count": 1000
        }
        res = scrape_session.get(GLPI_TICKET_URL, params=params, timeout=20)
        res.raise_for_status()
        
        # Extrai os chamados estruturados
        all_tickets = extract_tickets_html(res.text)
        
        # Ordena por data decrescente (mais recentes primeiro)
        all_tickets.sort(key=lambda x: x["parsed_date"], reverse=True)
        
        # Remove a chave temporária parsed_date antes do retorno JSON
        for t in all_tickets:
            if "parsed_date" in t:
                del t["parsed_date"]
                
        return jsonify(all_tickets)
        
    except requests.RequestException as e:
        return jsonify({"error": f"Erro ao coletar chamados do GLPI: {str(e)}"}), 502
    except Exception as e:
        return jsonify({"error": f"Falha no processamento dos chamados: {str(e)}"}), 500

@app.route("/api/auth/logout", methods=["POST"])
def auth_logout():
    token = session.pop("local_token", None)
    if token and token in active_sessions:
        scrape_session = active_sessions.pop(token)
        try:
            # Notifica o GLPI do encerramento da sessão
            scrape_session.get(f"{GLPI_BASE_URL}/front/logout.php", timeout=5)
        except Exception:
            pass
    return jsonify({"success": True})

def parse_details_from_soup(soup):
    followups = []
    tasks = []
    # Busca itens de acompanhamento usando hífen ou sublinhado (h_item, h-item, timeline_item, timeline-item)
    items = soup.find_all("div", class_=re.compile(r"(h[-_]item|timeline[-_]item)", re.IGNORECASE))
    if not items:
        items = soup.find_all("div", class_="tracking")
        
    for item in items:
        # Pega a div do conteúdo para verificar se é a descrição original do chamado (ITILContent)
        content_div = item.find("div", class_=re.compile(r"(h[-_]content|timeline[-_]content|content)", re.IGNORECASE))
        if content_div:
            classes = content_div.get("class", [])
            if "ITILContent" in classes:
                continue
                
        author = "Sistema"
        date_str = ""
        content = ""
        
        # Tenta encontrar cabeçalho de informação (autor/data)
        info_div = item.find("div", class_=re.compile(r"(h[-_]info|timeline[-_]info|info|header)", re.IGNORECASE))
        if info_div:
            # Procura primeiro pelo link do usuário que contém apenas o nome
            author_el = info_div.find("a", href=re.compile(r"user\.form\.php"))
            if not author_el:
                # Procura por qualquer link simples
                author_el = info_div.find("a")
            if not author_el:
                # Fallback para span/div excluindo tooltips
                for el in info_div.find_all(["span", "div"], class_=re.compile(r"(author|user|name)", re.IGNORECASE)):
                    if "tooltip" not in "".join(el.get("class", [])).lower():
                        author_el = el
                        break
            if author_el:
                author = author_el.get_text().strip()
                    
            date_el = info_div.find(["span", "div"], class_=re.compile(r"(date|time|timestamp)", re.IGNORECASE))
            if date_el:
                date_str = date_el.get_text().strip()
            else:
                txt = info_div.get_text(separator=" ")
                match = re.search(r'(\d{2}[-/]\d{2}[-/]\d{4}\s+\d{2}:\d{2})|(\d{4}[-/]\d{2}[-/]\d{2}\s+\d{2}:\d{2})', txt)
                if match:
                    date_str = match.group(0)
        else:
            author_el = item.find(class_=re.compile(r"(author|user|name)", re.IGNORECASE))
            date_el = item.find(class_=re.compile(r"(date|time|timestamp)", re.IGNORECASE))
            if author_el:
                author = author_el.get_text().strip()
            if date_el:
                date_str = date_el.get_text().strip()
                
        # Fallback para extrair data do texto do item
        if not date_str:
            txt = item.get_text(separator=" ")
            match = re.search(r'(\d{2}[-/]\d{2}[-/]\d{4}\s+\d{2}:\d{2})|(\d{4}[-/]\d{2}[-/]\d{2}\s+\d{2}:\d{2})', txt)
            if match:
                date_str = match.group(0)
        
        # Busca o conteúdo do comentário (prioriza rich_text_container ou item_content)
        body_div = item.find("div", class_=re.compile(r"(rich[-_]text[-_]container|item[-_]content|h[-_]content|timeline[-_]content|content|body)", re.IGNORECASE))
        if body_div:
            content = body_div.get_text(separator=" ").strip()
        else:
            content = item.get_text().strip()
            if info_div:
                content = content.replace(info_div.get_text(), "", 1).strip()
        
        content_clean = re.sub(r'\s+', ' ', content).strip()
        if not content_clean or "mais detalhes" in content_clean.lower() or content_clean == "Sem descrição detalhada.":
            continue
            
        # Verificação se o item é uma TAREFA (TicketTask)
        item_html = str(item)
        task_match = re.search(r'change_task_state\(\s*(\d+)\s*,', item_html)
        
        if task_match:
            task_id = task_match.group(1)
            # Determina o estado da tarefa verificando se a classe da tag de estado é state_2 (feito) ou state_1 (a fazer)
            status_el = item.find(attrs={"onclick": re.compile(r'change_task_state\(\s*' + task_id + r'\s*,')})
            if not status_el:
                status_el = item.find(class_=re.compile(r'state_[12]'))
                
            task_state = "todo"
            if status_el:
                classes = status_el.get("class", [])
                classes_str = " ".join(classes)
                if "state_2" in classes_str:
                    task_state = "done"
                    
            tasks.append({
                "id": task_id,
                "author": simplify_technician_name(author),
                "date": date_str if date_str else "Sem data",
                "content": content_clean,
                "state": task_state
            })
        else:
            followups.append({
                "author": simplify_technician_name(author),
                "date": date_str if date_str else "Sem data",
                "content": content_clean
            })
            
    return followups, tasks

@app.route("/api/tickets/<ticket_id>/details", methods=["GET"])
def get_ticket_details(ticket_id):
    token = session.get("local_token")
    if not token or token not in active_sessions:
        return jsonify({"error": "Sessão expirada ou não autenticada."}), 401
        
    scrape_session = active_sessions[token]
    
    try:
        followups, tasks = [], []
        ajax_headers = {"X-Requested-With": "XMLHttpRequest"}
        
        # 1. Tenta carregar do ajax/common.tabs.php com Ticket$1 e _target (padrão verificado para a timeline do GLPI 9.5)
        url_tabs1 = f"{GLPI_BASE_URL}/ajax/common.tabs.php?_target=/front/ticket.form.php&_itemtype=Ticket&_glpi_tab=Ticket$1&id={ticket_id}"
        try:
            res = scrape_session.get(url_tabs1, headers=ajax_headers, timeout=10)
            if res.ok and res.text.strip():
                soup = BeautifulSoup(res.text, "html.parser")
                followups, tasks = parse_details_from_soup(soup)
        except Exception as e:
            print(f"Erro ao buscar common.tabs.php Ticket$1 para ID {ticket_id}: {e}")
            
        # 2. Se não trouxe nada, tenta common.tabs.php com ITILFollowup$1 e _target
        if not followups and not tasks:
            url_tabs2 = f"{GLPI_BASE_URL}/ajax/common.tabs.php?_target=/front/ticket.form.php&_itemtype=Ticket&_glpi_tab=ITILFollowup$1&id={ticket_id}"
            try:
                res = scrape_session.get(url_tabs2, headers=ajax_headers, timeout=10)
                if res.ok and res.text.strip():
                    soup = BeautifulSoup(res.text, "html.parser")
                    followups, tasks = parse_details_from_soup(soup)
            except Exception as e:
                print(f"Erro ao buscar common.tabs.php ITILFollowup$1 para ID {ticket_id}: {e}")
                
        # 3. Se não trouxe nada, tenta common.tabs.php com CommonITILObject$1 e _target
        if not followups and not tasks:
            url_tabs3 = f"{GLPI_BASE_URL}/ajax/common.tabs.php?_target=/front/ticket.form.php&_itemtype=Ticket&_glpi_tab=CommonITILObject$1&id={ticket_id}"
            try:
                res = scrape_session.get(url_tabs3, headers=ajax_headers, timeout=10)
                if res.ok and res.text.strip():
                    soup = BeautifulSoup(res.text, "html.parser")
                    followups, tasks = parse_details_from_soup(soup)
            except Exception as e:
                print(f"Erro ao buscar common.tabs.php CommonITILObject$1 para ID {ticket_id}: {e}")
                
        # 4. Tenta carregar do ajax/timeline.php (padrão de timeline de outras versões)
        if not followups and not tasks:
            url_timeline = f"{GLPI_BASE_URL}/ajax/timeline.php?itemtype=Ticket&parent_id={ticket_id}"
            try:
                res = scrape_session.get(url_timeline, headers=ajax_headers, timeout=10)
                if res.ok and res.text.strip():
                    soup = BeautifulSoup(res.text, "html.parser")
                    followups, tasks = parse_details_from_soup(soup)
            except Exception as e:
                print(f"Erro ao buscar timeline.php para ID {ticket_id}: {e}")
                
        # 5. Tenta viewSubItems.php
        if not followups and not tasks:
            url_subitems = f"{GLPI_BASE_URL}/ajax/viewSubItems.php?itemtype=Ticket&parent_id={ticket_id}"
            try:
                res = scrape_session.get(url_subitems, headers=ajax_headers, timeout=10)
                if res.ok and res.text.strip():
                    soup = BeautifulSoup(res.text, "html.parser")
                    followups, tasks = parse_details_from_soup(soup)
            except Exception as e:
                print(f"Erro ao buscar viewSubItems.php para ID {ticket_id}: {e}")
                
        # 6. Fallback final: tenta carregar a página principal do chamado (ticket.form.php)
        if not followups and not tasks:
            url_form = f"{GLPI_BASE_URL}/front/ticket.form.php?id={ticket_id}"
            try:
                res = scrape_session.get(url_form, timeout=10)
                if res.ok and res.text.strip():
                    soup = BeautifulSoup(res.text, "html.parser")
                    followups, tasks = parse_details_from_soup(soup)
            except Exception as e:
                print(f"Erro ao buscar ticket.form.php para ID {ticket_id}: {e}")

        # Se ainda assim estiver vazio, escrevemos informações de debug no arquivo
        if not followups and not tasks:
            try:
                debug_path = "c:/GLPI/debug_timeline_empty.html"
                with open(debug_path, "w", encoding="utf-8") as f_dbg:
                    f_dbg.write(f"Timeline URL: {url_tabs1}\nStatus: 200\nContent length: {len(res.text) if 'res' in locals() else 0}\n\n")
            except Exception as e:
                print(f"Erro ao salvar debug HTML: {e}")
                
        last_interaction_date = ""
        all_interactions = followups + tasks
        if all_interactions:
            all_interactions.sort(key=lambda x: parse_glpi_date(x["date"]), reverse=True)
            latest_interaction = all_interactions[0]
            latest_date_str = latest_interaction["date"]
            parsed_latest = parse_glpi_date(latest_date_str)
            if parsed_latest != datetime.min:
                last_interaction_date = parsed_latest.strftime("%d/%m/%Y")
                
        return jsonify({
            "last_interaction": last_interaction_date,
            "followups": followups,
            "tasks": tasks
        })
        
    except requests.RequestException as e:
        return jsonify({"error": f"Erro de comunicação com o GLPI: {str(e)}"}), 502
    except Exception as e:
        return jsonify({"error": f"Erro de processamento de detalhes: {str(e)}"}), 500

@app.route("/api/tickets/<ticket_id>/tasks/<task_id>/toggle", methods=["POST"])
def toggle_task(ticket_id, task_id):
    token = session.get("local_token")
    if not token or token not in active_sessions:
        return jsonify({"error": "Sessão expirada ou não autenticada."}), 401
        
    scrape_session = active_sessions[token]
    ajax_headers = {
        "X-Requested-With": "XMLHttpRequest",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
    
    url = f"{GLPI_BASE_URL}/ajax/timeline.php"
    payload = {
        "action": "change_task_state",
        "tasks_id": task_id,
        "parenttype": "Ticket",
        "tickets_id": ticket_id
    }
    
    try:
        res = scrape_session.post(url, data=payload, headers=ajax_headers, timeout=10)
        res.raise_for_status()
        
        response_data = res.json()
        new_state = "done" if response_data.get("state") == 2 else "todo"
        
        return jsonify({
            "success": True,
            "new_state": new_state
        })
    except Exception as e:
        return jsonify({"error": f"Falha ao alterar estado da tarefa no GLPI: {str(e)}"}), 500

@app.route("/api/test_parse/<ticket_id>")
def test_parse(ticket_id):
    token = session.get("local_token")
    if not token or token not in active_sessions:
        return "Unauthorized", 401
    scrape_session = active_sessions[token]
    
    ajax_headers = {
        "X-Requested-With": "XMLHttpRequest",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
    
    url_tabs = f"{GLPI_BASE_URL}/ajax/common.tabs.php?_target=/front/ticket.form.php&_itemtype=Ticket&_glpi_tab=Ticket$1&id={ticket_id}"
    try:
        res = scrape_session.get(url_tabs, headers=ajax_headers, timeout=10)
        res.raise_for_status()
        
        # Salva o HTML completo para inspeção
        with open("c:/GLPI/debug_tabs_get.html", "w", encoding="utf-8") as f:
            f.write(res.text)
            
        soup = BeautifulSoup(res.text, "html.parser")
        followups, tasks = parse_details_from_soup(soup)
        
        # Coleta itens brutos div de timeline/h-item para debug
        raw_items = []
        # Encontra todas as divs para vermos a estrutura completa do HTML retornado
        all_divs = soup.find_all("div")
        for div in all_divs:
            classes = div.get("class", [])
            # Se a classe contiver palavras chaves chaves ou se for filha de timeline
            classes_str = " ".join(classes).lower()
            if any(k in classes_str for k in ["timeline", "followup", "task", "solution", "item", "tracking", "history"]):
                raw_items.append({
                    "tag": div.name,
                    "classes": classes,
                    "text_preview": div.get_text(separator=" ").strip()[:150]
                })
            
        return jsonify({
            "status": "success",
            "url_queried": url_tabs,
            "html_length": len(res.text),
            "raw_items_found": len(raw_items),
            "raw_items_list": raw_items[:50],  # Limita a 50 itens para o JSON ficar legível
            "parsed_followups": followups,
            "parsed_tasks": tasks
        })
    except Exception as e:
        return jsonify({
            "status": "error",
            "error": str(e)
        }), 500

if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000, debug=True)
