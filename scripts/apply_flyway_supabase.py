import os
import glob
import re
import sys
import psycopg2

def apply_migrations():
    supabase_url = sys.argv[1] if len(sys.argv) > 1 else os.getenv("SUPABASE_DB_URL")
    if not supabase_url:
        print("[ERROR] SUPABASE_DB_URL e necessario.")
        sys.exit(1)

    migration_dir = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "db", "migration")
    sql_files = glob.glob(os.path.join(migration_dir, "V*.sql"))

    def extract_version(filepath):
        filename = os.path.basename(filepath)
        match = re.match(r"^V(\d+)__", filename)
        return int(match.group(1)) if match else 999

    sql_files.sort(key=extract_version)

    print(f"[INFO] Encontrados {len(sql_files)} arquivos de migracao.")
    print("[INFO] Conectando ao Supabase PostgreSQL via host...")

    try:
        conn = psycopg2.connect(supabase_url)
        conn.autocommit = True
        cursor = conn.cursor()
        print("[OK] Conexao com Supabase estabelecida com sucesso!")

        for filepath in sql_files:
            filename = os.path.basename(filepath)
            print(f"[INFO] Aplicando migracao: {filename}...")
            with open(filepath, "r", encoding="utf-8") as f:
                sql_script = f.read()
            cursor.execute(sql_script)
            print(f"[OK] {filename} aplicada com sucesso.")

        cursor.close()
        conn.close()
        print("[OK] TODAS AS MIGRACOES FLYWAY (V1..V23) FORAM APLICADAS NO SUPABASE COM SUCESSO!")

    except Exception as e:
        print(f"[ERROR] Falha ao aplicar migracoes no Supabase: {e}")
        sys.exit(1)

if __name__ == "__main__":
    apply_migrations()
