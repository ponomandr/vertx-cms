# 1. Generate a Self-Signed Root CA Certificate

# Generate CA Private Key
openssl genrsa -out ca.key 4096

# Generate Self-Signed Root Certificate
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 -out ca.crt -subj "/CN=Demo CA"


# 2. Generate Server Certificate (mTLS)

# Create Server Key
openssl genrsa -out server.key 2048

# Create Server CSR
openssl req -new -key server.key -out server.csr -subj "/CN=Demo Sever"

# Create Extension Config File
cat > server.ext << EOF
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost, IP:127.0.0.1
EOF

# Sign Server Certificate with CA
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 730 -sha256 -extfile server.ext


# 3. Generate Client Certificate (mTLS)

# Create Client Key
openssl genrsa -out client-tls.key 2048

# Create Client CSR
openssl req -new -key client-tls.key -out client-tls.csr -subj "/CN=Demo Client"

# Create Extension Config File:
cat > client-tls.ext << EOF
extendedKeyUsage = clientAuth
EOF

# Sign mTLS Client Certificate with CA
openssl x509 -req -in client-tls.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client-tls.crt -days 730 -sha256 -extfile client-tls.ext

# Create CMS Key
openssl genrsa -out client-cms.key 2048

# Create CMS CSR
openssl req -new -key client-cms.key -out client-cms.csr -subj "/CN=Demo Client"

# Create Extension Config File
cat > client-cms.ext << EOF
keyUsage = digitalSignature
EOF

# Sign CMS Signing Certificate with CA
openssl x509 -req -in client-cms.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client-cms.crt -days 730 -sha256 -extfile client-cms.ext


# 4. Package Results into PKCS#12 Keystores and Truststores

# Server Keystore (Private Key + Cert)
openssl pkcs12 -export -out server-tls-keystore.p12 -name server-tls -inkey server.key -in server.crt -passout pass:password

# Server Truststore (Trusted CA)
keytool -importcert -file ca.crt -alias ca -keystore server-truststore.p12 -storetype PKCS12 -noprompt -storepass password

# Client Keystore (mTLS Key/Cert)
openssl pkcs12 -export -out client-tls-keystore.p12 -name client-tls -inkey client-tls.key -in client-tls.crt -passout pass:password

# Client Keystore (CMS Key/Cert)
openssl pkcs12 -export -out client-cms-keystore.p12 -name client-cms -inkey client-cms.key -in client-cms.crt -passout pass:password

# Client Truststore (Trusted CA)
keytool -importcert -file ca.crt -alias ca -keystore client-truststore.p12 -storetype PKCS12 -noprompt -storepass password

