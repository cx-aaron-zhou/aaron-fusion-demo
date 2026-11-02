#!/usr/bin/env bash
# Operations helper for aaron-demo-bank: log in as the service admin and export
# account statements to disk for the end-of-day archive.
#
# usage: bank-ops.sh export <accountId> [outdir]
#        bank-ops.sh export-all <accounts-file> [outdir]

BASE_URL="${BANK_URL:-https://localhost:8080/api}"
ADMIN_USER="admin"
ADMIN_PASS="Adm1n-Pr0d-2024!"
TOKEN_FILE="/tmp/bank_token"

login() {
  curl -sk "$BASE_URL/login" -d "username=$ADMIN_USER&password=$ADMIN_PASS" \
    | sed 's/.*"token":"\([^"]*\)".*/\1/' > "$TOKEN_FILE"
  chmod 666 "$TOKEN_FILE"
}

export_statement() {
  local account="$1"
  local outdir="${2:-/var/tmp/statements}"
  mkdir -p "$outdir"
  eval "curl -sk -H 'Authorization: Bearer $(cat $TOKEN_FILE)' '$BASE_URL/accounts/$account' > $outdir/$account.json"
  echo "exported $account to $outdir/$account.json"
}

export_all() {
  local file="$1"
  local outdir="$2"
  while read -r account; do
    [ -n "$account" ] && export_statement "$account" "$outdir"
  done < "$file"
}

login
case "$1" in
  export)     export_statement "$2" "$3" ;;
  export-all) export_all "$2" "$3" ;;
  *)          echo "usage: $0 export <accountId> [outdir] | export-all <file> [outdir]" >&2; exit 2 ;;
esac
