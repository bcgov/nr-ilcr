// Local real-FAM-login example (DEV Cognito client).
//
// Copy this over `public/amplify-config.js` to exercise the real Cognito Hosted UI locally:
//     cp amplify-config.local.example.js public/amplify-config.js
// Do NOT commit your edited public/amplify-config.js — the repo default must stay { mockUser: true }.
//
// These are public identifiers (Cognito pool + SPA web-client id), not secrets. Run the backend with
// security on so /api/v1/me validates the real token:
//     ILCR_SECURITY_ENABLED=true COGNITO_REGION=ca-central-1 \
//     COGNITO_USER_POOL=ca-central-1_UpeAqsYt4 COGNITO_CLIENT_ID=352pis0ark86dam7ht1jlp9uj5 \
//     SPRING_PROFILES_ACTIVE=oracle,openshift ./mvnw spring-boot:run
//
// Confirm with the FAM admin (Ian): the exact cognitoDomain, and that your IDIR account carries an
// ILCR group in the DEV pool (otherwise you correctly land on the No-access screen). Values from
// Ian's 2026-07-28 FAM config; the DEV client allow-lists http://localhost:3000/.
window.amplifyConfig = {
  mockUser: false,
  userPoolId: 'ca-central-1_UpeAqsYt4',
  userPoolClientId: '352pis0ark86dam7ht1jlp9uj5', // DEV web client
  cognitoDomain: 'lza-prod-fam-user-pool-domain.auth.ca-central-1.amazoncognito.com',
  redirectSignIn: 'http://localhost:3000/',
  redirectSignOut:
    'https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi?retnow=1&returl=https://dev.loginproxy.gov.bc.ca/auth/realms/standard/protocol/openid-connect/logout?redirect_uri=http://localhost:3000/logout',
}
