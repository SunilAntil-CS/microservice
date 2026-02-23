## JWT Study Notes – Q&A Format

### What is JWT?
**JWT (JSON Web Token)** is an open standard (RFC 7519) that defines a compact and self-contained way to securely transmit information between parties as a JSON object. It is digitally signed, so it can be verified and trusted.

### Why is JWT used in microservices?
JWTs are commonly used for authentication and authorization in stateless microservices architectures. They allow the API gateway or resource server to verify the identity of a client without needing to query a session store or database. The token itself contains all necessary information (user ID, roles, expiration) and is cryptographically signed.

### What are the three parts of a JWT?
A JWT consists of three parts separated by dots (`.`):
- **Header**: Contains metadata about the token, typically the signing algorithm (e.g., `RS256`).
- **Payload**: Contains the **claims** – statements about the user and any additional data (e.g., `sub` (subject/user ID), `name`, `roles`, `exp` (expiration time)).
- **Signature**: Created by taking the encoded header, encoded payload, a secret (for HMAC) or a private key (for RSA), and signing them. The signature is used to verify that the token hasn't been tampered with.

### How can anyone decode the payload if it's not encrypted?
The header and payload are only **Base64Url‑encoded**, not encrypted. This means anyone can decode them (as demonstrated by [jwt.io](https://jwt.io)). That is intentional – the information inside is meant to be readable by the client and any intermediate service. The **security** comes from the signature, not from hiding the data. Sensitive information should never be placed in a plain JWT; if needed, JWE (JSON Web Encryption) can be used.

### If the payload is readable, how is JWT secure?
The signature ensures the token's integrity. An attacker cannot modify the payload without invalidating the signature. When your gateway receives a JWT, it recomputes the signature using the public key and compares it to the token's signature. If they don't match, the token is rejected.

### What is the difference between signing and encryption?
- **Signing** (JWS) proves the token was issued by a trusted party and hasn't been altered. It does not hide the contents.
- **Encryption** (JWE) hides the contents from anyone who does not have the decryption key. For authentication, signing is usually sufficient because the claims are not secrets.

### How does the gateway verify the signature?
The gateway (acting as an OAuth2 resource server) is configured with the **public key** of the issuer. It can obtain this key via a **JWKS endpoint** (JSON Web Key Set) or a static key. When a request arrives, Spring Security's `ReactiveJwtDecoder`:
1. Splits the token into header, payload, and signature.
2. Fetches the appropriate public key (cached) and uses it to verify the signature.
3. Checks standard claims like `exp` (expiration), `iss` (issuer), and `aud` (audience).
4. If all checks pass, it creates an authenticated principal (`Jwt` object) and places it in the security context.

### If the public key is publicly known, can someone forge a token?
No. The public key can only **verify** a signature; it cannot **create** a new valid signature. Signature creation requires the **private key**, which is kept secret by the issuer (the authentication server). As long as the private key is secure, forged tokens are impossible.

### What is the difference between authentication and authorization?
- **Authentication** answers "Who are you?" (identity).
- **Authorization** answers "What are you allowed to do?" (permissions).

A JWT often contains both: the `sub` claim identifies the user, and `roles` or `scope` claims define what the user can do. In your gateway, after validating the JWT, you extract these claims and forward them to backend services (e.g., via `X-User-ID` and `X-User-Roles` headers).

### What is OAuth 2.0?
OAuth 2.0 is an **authorization framework** that enables a third-party application to obtain limited access to a user's resources without exposing the user's credentials. It defines roles (resource owner, client, authorization server, resource server) and grant types for obtaining access tokens. It is a **protocol** at the application layer, running over HTTP(S).

### What is OpenID Connect (OIDC)?
OIDC is an identity layer built on top of OAuth 2.0. It adds an **ID token** (a JWT containing identity claims) alongside the access token. The ID token is intended for the **client** to authenticate the user. The access token is for the **resource server** (your gateway) to authorize access to APIs. In your gateway, you only need the access token.

### How does Spring Boot's `oauth2ResourceServer()` work?
The `oauth2ResourceServer()` configuration in `SecurityWebFilterChain` enables the gateway to act as an OAuth2 resource server. The `.jwt(jwt -> {})` part tells Spring to use JWT as the token format. Spring Boot auto-configures a `ReactiveJwtDecoder` based on properties like `spring.security.oauth2.resourceserver.jwt.issuer-uri` or `jwk-set-uri`. The decoder automatically fetches public keys, validates signatures, and populates the security context.

### Why use Spring Security's OAuth2 resource server instead of manual validation?
- **Automatic key management**: It caches and refreshes public keys from the issuer's JWKS endpoint.
- **Comprehensive claim validation**: Handles standard claims (exp, nbf, iss, aud) out of the box.
- **Reactive and non‑blocking**: Designed for reactive stacks.
- **Seamless integration**: Populates the security context, so you can easily access the `Jwt` object.
- **Error handling**: Returns proper `401` responses with correct headers.
- **Battle‑tested and secure**: Follows best practices and guards against common attacks.

### What is the purpose of the custom `JwtAuthenticationFilter` you wrote?
After Spring Security has validated the JWT and placed a `Jwt` object in the security context, your filter extracts the user ID and roles from the token and adds them as headers (`X-User-ID`, `X-User-Roles`) to the outgoing request to backend services. This way, downstream services can identify the caller without parsing the token again. The filter does **not** perform validation – it relies on Spring Security to have already done that.

### Why does your filter have an unused `writeJsonError` method?
That method was a leftover from an earlier design where the filter might have done its own validation. In the current design, Spring Security handles all authentication failures before your filter runs, so the method is not called. For production, you should replace it with proper `AuthenticationEntryPoint` and `AccessDeniedHandler` as discussed.

### How should production handle authentication errors?
Instead of handling errors in a `GlobalFilter`, you should configure:
- A custom `ServerAuthenticationEntryPoint` for `401 Unauthorized`.
- A custom `ServerAccessDeniedHandler` for `403 Forbidden`.

These are registered in the `SecurityWebFilterChain` and produce consistent JSON error responses, aligning with your API design.

### How is access denied (403) handled in a microservices architecture?
- **Coarse‑grained authorization** (role‑based) can be done in the gateway using JWT claims (e.g., `.hasRole("ADMIN")`). Spring Security will reject the request with 403 if the role is missing, and your `AccessDeniedHandler` will format the response.
- **Fine‑grained authorization** (resource ownership) is delegated to the backend services. The gateway forwards the user identity (e.g., via `X-User-ID`) and lets the service query its database to decide if the user can perform the action. If not, the service returns a 403, which the gateway passes back.

### What is the role of the private key in JWT?
The private key is used by the **issuer** (authentication server) to **sign** the JWT. It must be kept secret. The corresponding public key is distributed to all resource servers (like your gateway) so they can verify the signature. This asymmetric cryptography ensures that only the issuer can create valid tokens.

### What is a JWKS endpoint?
A JWKS (JSON Web Key Set) endpoint is a URL served by the authorization server that returns a set of public keys in JSON format. Your gateway can be configured with this endpoint, and Spring Security will automatically fetch and cache the keys, and use them to verify JWT signatures. This allows the issuer to rotate keys without requiring changes in the gateway.

### Why is JWT considered stateless?
Because the token itself contains all the information needed to authenticate and authorize the request. The gateway does not need to store session data; it simply validates the token on each request. This makes scaling easier – any gateway instance can handle any request.

### Can JWT be used for single sign‑on (SSO)?
Yes. With OIDC, a user can log in once at an identity provider and obtain an ID token and access token. These tokens can be used across multiple applications (if they trust the same issuer), enabling SSO.

### What are the security best practices for JWTs?
- Use a strong signing algorithm (e.g., RS256, ES256).
- Keep the private key secure; rotate it periodically.
- Set short expiration times (`exp` claim) to limit token lifetime.
- Validate the `aud` (audience) claim to ensure the token is intended for your service.
- Never store sensitive data in the payload (unless encrypted).
- Use HTTPS to prevent token interception.
- Implement proper error handling and avoid leaking information in error messages.

---

