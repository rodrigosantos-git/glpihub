import threading
import time
import sys
import os
import webview
from server import app

def start_flask():
    """ Inicia o servidor Flask na porta 5000 do localhost """
    # Desativa reloader do Flask para evitar que processos duplicados abram janelas extras da webview
    app.run(host="127.0.0.1", port=5000, debug=False, use_reloader=False)

if __name__ == "__main__":
    # Inicia a thread daemon para o servidor Flask
    server_thread = threading.Thread(target=start_flask, daemon=True)
    server_thread.start()
    
    # Pequena pausa para certificar a escuta da porta do Flask
    time.sleep(1.0)
    
    # Cria a janela principal do Desktop
    webview.create_window(
        title="GLPI HUB Desktop",
        url="http://127.0.0.1:5000",
        width=1280,
        height=800,
        resizable=True,
        min_size=(900, 600)
    )
    
    # Inicializa o loop da GUI
    webview.start()
