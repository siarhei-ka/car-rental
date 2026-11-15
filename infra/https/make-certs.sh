#!/bin/sh
# Makes a local certificate authority and, signed by it, the certificate the HTTPS proxy serves for
# this computer's address on the network and for localhost.
#
#   infra/https/make-certs.sh 192.168.0.41
#
# Writes infra/https/certs/ (ignored by git):
#   ca.crt      the authority. Trust it once on each phone and computer that opens the system
#   ca.key      its key. Stays on this computer, and is never shared
#   server.crt  the certificate of the proxy; made again on each run, for a new address
#   server.key
# The authority is made only if it is missing, so phones that trust it keep trusting it.
set -eu

host="${1:?Give the address of this computer on the network, such as 192.168.0.41}"
dir="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$dir"
cd "$dir"
umask 077

if [ ! -f ca.key ]; then
  openssl req -x509 -newkey rsa:3072 -sha256 -days 3650 -nodes \
    -keyout ca.key -out ca.crt \
    -subj "/CN=Car Rental local CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
fi

case "$host" in
  *[!0-9.]*) san="DNS:$host" ;;
  *) san="IP:$host" ;;
esac

cat > server.ext <<EXT
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=$san,DNS:localhost,IP:127.0.0.1
EXT
openssl req -newkey rsa:2048 -sha256 -nodes -keyout server.key -out server.csr \
  -subj "/CN=$host" 2>/dev/null
# Phones accept a server certificate for at most 825 days.
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days 825 -sha256 -extfile server.ext -out server.crt 2>/dev/null
rm -f server.csr server.ext ca.srl
# The certificates are public; the keys stay readable by this user only.
chmod 644 ca.crt server.crt

echo "Certificate for $host and localhost in $dir"
