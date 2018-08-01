/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) [2018] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */
package fish.payara.security.oidc.controller;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWEDecryptionKeySelector;
import com.nimbusds.jose.proc.JWEKeySelector;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import static fish.payara.security.oidc.api.OidcConstant.ACCESS_TOKEN_HASH;
import fish.payara.security.oidc.domain.OidcConfiguration;
import static fish.payara.security.oidc.api.OidcConstant.AUTHORIZATION_CODE;
import static fish.payara.security.oidc.api.OidcConstant.CLIENT_ID;
import static fish.payara.security.oidc.api.OidcConstant.CLIENT_SECRET;
import static fish.payara.security.oidc.api.OidcConstant.CODE;
import static fish.payara.security.oidc.api.OidcConstant.GRANT_TYPE;
import static fish.payara.security.oidc.api.OidcConstant.REDIRECT_URI;
import fish.payara.security.oidc.api.OidcContext;
import fish.payara.security.oidc.domain.OidcNonce;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Map;
import static java.util.Objects.isNull;
import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Controller for Token endpoint
 *
 * @author Gaurav Gupta
 */
@ApplicationScoped
public class TokenController {

    private static final String DEFAULT_SIGNING_ALGORITHM = "RS256";

    /**
     * (4) A Client makes a token request to the token endpoint and the OpenId
     * Provider responds with an ID Token and an Access Token.
     *
     * @param configuration
     * @param request
     * @return a JSON object representation of OpenID Connect token response
     * from the Token endpoint.
     */
    public Response getTokens(OidcConfiguration configuration, HttpServletRequest request) {
        /**
         * one-time authorization code that RP exchange for an Access / Id token
         */
        String authorizationCode = request.getParameter(CODE);

        /**
         * The Client sends the parameters to the Token Endpoint using the Form
         * Serialization with all parameters to :
         *
         * 1. Authenticate client using CLIENT_ID & CLIENT_SECRET <br>
         * 2. Verify that the Authorization Code is valid <br>
         * 3. Ensure that the redirect_uri parameter value is identical to the
         * initial authorization request's redirect_uri parameter value.
         */
        Form form = new Form()
                .param(CLIENT_ID, configuration.getClientID())
                .param(CLIENT_SECRET, new String(configuration.getClientSecret()))
                .param(GRANT_TYPE, AUTHORIZATION_CODE)
                .param(CODE, authorizationCode)
                .param(REDIRECT_URI, configuration.getRedirectURI());

        //  ID Token and Access Token Request
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(configuration.getProviderMetadata().getTokenEndpoint());
        Response response = target.request()
                .accept(APPLICATION_JSON)
                .post(Entity.form(form));
        return response;
    }

    /**
     * Validates the ID token and retrieves the End-User's subject identifier
     * and other claims.
     *
     * @param configuration
     * @param nonce
     * @param idToken
     * @return id token claims
     */
    public JWT parseIdToken(OidcConfiguration configuration, OidcNonce nonce, String idToken) {
        try {
            /**
             * The ID tokens are in JSON Web Token (JWT) format.
             */
            JWT idTokenJWT = JWTParser.parse(idToken);
            return idTokenJWT;
        } catch (ParseException ex) {
            throw new IllegalStateException("Error in parsing the Id Token", ex);
        }
    }

    /**
     * (5.1) Validate Id Token's claims and verify ID Token's signature.
     *
     * @param configuration
     * @param nonce
     * @param idToken
     * @return JWT Claims
     */
    public Map<String, Object> validateIdToken(OidcConfiguration configuration, OidcNonce nonce, JWT idToken) {
        JWTClaimsSet claimsSet;
        try {

            if (idToken instanceof PlainJWT) {
                PlainJWT plainToken = (PlainJWT) idToken;
                try {
                    claimsSet = plainToken.getJWTClaimsSet();
                    JWTClaimsSetVerifier jwtVerifier = new IdTokenClaimsSetVerifier(configuration, nonce);
                    jwtVerifier.verify(claimsSet, null);
                } catch (ParseException | BadJWTException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            } else if (idToken instanceof SignedJWT) {
                SignedJWT signedToken = (SignedJWT) idToken;
                JWSHeader header = signedToken.getHeader();
                String alg = header.getAlgorithm().getName();
                if (isNull(alg)) {
                    // set the default value
                    alg = DEFAULT_SIGNING_ALGORITHM;
                }

                ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
                jwtProcessor.setJWSKeySelector(getJWSKeySelector(configuration, alg));
                jwtProcessor.setJWTClaimsSetVerifier(new IdTokenClaimsSetVerifier(configuration, nonce));
                claimsSet = jwtProcessor.process(signedToken, null);

//            String kid = header.getKeyID();
//            JsonObject jwk = getJWK(configuration, alg, kid);
//            /**
//             * Build the public key using JSON Web Key (JWK)
//             */
//            Base64URL modulus = new Base64URL(jwk.getString("n"));
//            Base64URL publicExponent = new Base64URL(jwk.getString("e"));
//            RSAKey publicKey = new RSAKey.Builder(modulus, publicExponent)
//                    .keyUse(KeyUse.SIGNATURE)
//                    .keyID(kid)
//                    .build();
//            new JWSVerificationKeySelector(expectedJWSAlg, new ImmutableSecret(clientSecret.getValueBytes()))
//            // verify the ID Token using public key
//            try {
//                if (!signedToken.verify(new RSASSAVerifier(jwk))) {
//                    throw new IllegalStateException("Signature invalid");
//                }
//            } catch (JOSEException ex) {
//                throw new IllegalStateException("Error in verifying the Id Token", ex);
//            }
            } else if (idToken instanceof EncryptedJWT) { // If ID Token is encrypted, decrypt it using the keys and algorithms
                EncryptedJWT encryptedToken = (EncryptedJWT) idToken;
                JWEHeader header = encryptedToken.getHeader();
                String alg = header.getAlgorithm().getName();

                ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
                jwtProcessor.setJWSKeySelector(getJWSKeySelector(configuration, alg));
                jwtProcessor.setJWEKeySelector(getJWEKeySelector(configuration));
                jwtProcessor.setJWTClaimsSetVerifier(new IdTokenClaimsSetVerifier(configuration, nonce));
                claimsSet = jwtProcessor.process(encryptedToken, null);
            } else {
                throw new IllegalStateException("Unexpected JWT type: " + idToken.getClass());
            }
        } catch (BadJOSEException | JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        return claimsSet.getClaims();
    }

    private JWSKeySelector getJWSKeySelector(OidcConfiguration configuration, String alg) {
        JWSKeySelector jwsKeySelector;
        JWKSource jwkSource;
        JWSAlgorithm jwsAlg = new JWSAlgorithm(alg);
        if (Algorithm.NONE.equals(jwsAlg)) {
            // Skip creation of JWS key selector, plain ID tokens expected
            throw new IllegalStateException("Unsupported JWS algorithm: " + jwsAlg);
        } else if (JWSAlgorithm.Family.RSA.contains(jwsAlg) || JWSAlgorithm.Family.EC.contains(jwsAlg)) {
            try {
                jwkSource = new RemoteJWKSet(new URL(configuration.getProviderMetadata().getJwksUri()));
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
            jwsKeySelector = new JWSVerificationKeySelector(jwsAlg, jwkSource);
        } else if (JWSAlgorithm.Family.HMAC_SHA.contains(jwsAlg)) {
            byte[] clientSecret = new String(configuration.getClientSecret()).getBytes(UTF_8);
            if (isNull(clientSecret)) {
                throw new IllegalStateException("Missing client secret");
            }
            return new JWSVerificationKeySelector(jwsAlg, new ImmutableSecret(clientSecret));
        } else {
            throw new IllegalStateException("Unsupported JWS algorithm: " + jwsAlg);
        }
        return jwsKeySelector;
    }

    private JWEKeySelector getJWEKeySelector(OidcConfiguration configuration) {
        JWEKeySelector jweKeySelector;

        JWEAlgorithm jwsAlg = configuration.getEncryptionMetadata().getEncryptionAlgorithm();
        EncryptionMethod jweEnc = configuration.getEncryptionMetadata().getEncryptionMethod();
        JWKSource jwkSource = configuration.getEncryptionMetadata().getPrivateKeySource();

        if (isNull(jwsAlg)) {
            throw new IllegalStateException("Missing JWE encryption algorithm ");
        }
        if (!configuration.getProviderMetadata().getIdTokenEncryptionAlgorithmsSupported().contains(jwsAlg.getName())) {
            throw new IllegalStateException("Unsupported ID tokens algorithm :" + jwsAlg.getName());
        }
        if (isNull(jweEnc)) {
            throw new IllegalStateException("Missing JWE encryption method");
        }
        if (!configuration.getProviderMetadata().getIdTokenEncryptionMethodsSupported().contains(jweEnc.getName())) {
            throw new IllegalStateException("Unsupported ID tokens encryption method :" + jweEnc.getName());
        }

        jweKeySelector = new JWEDecryptionKeySelector(jwsAlg, jweEnc, jwkSource);
        return jweKeySelector;
    }

    /**
     * Retrieve the JSON Web Key Set (JWKS) from jwks_uri endpoint and filter
     * for potential signing keys in the JWKS with a matching alg & kid
     * property.
     *
     * @param configuration
     * @param alg the algorithm for the key
     * @param kid the unique identifier for the key
     * @return the JSON Web Key (JWK), a cryptographic key
     */
    private JsonObject getJWK(OidcConfiguration configuration, String alg, String kid) {

        /**
         * Fetch the JSON Web Key Set (JWKS), the set of keys containing the
         * public keys that should be used to verify any JWT issued by the
         * authorization server.
         */
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(configuration.getProviderMetadata().getJwksUri());
        Response response = target.request()
                .accept(APPLICATION_JSON)
                .get();
        if (response.getStatus() != Status.OK.getStatusCode()) {
            throw new IllegalStateException("Unable to fetch the JSON Web Key Set (JWKS) from jwks_uri endpoint : " + configuration.getProviderMetadata().getJwksUri());
        }
        String jwksBody = response.readEntity(String.class);

//        JWKSet jwks;
//        try {
//            jwks = JWKSet.parse(jwksBody);
//        } catch (ParseException ex) {
//            throw new IllegalStateException(ex);
//        }
//
//        /**
//         * Get the JWK from JSON Web Key set (JWKS) as identified by its KeyId
//         * (kid).
//         */
//        JWK jwk = jwks.getKeyByKeyId(kid);
//        if (jwk == null) {
//            throw new IllegalStateException();
//        } else if (!jwk.getAlgorithm().getName().equals(alg)) {
//            throw new IllegalStateException();
//        }
//
//        return jwk;
        JsonObject jwksObject = Json.createReader(new StringReader(jwksBody)).readObject();

        /**
         * Find the signing key in the JWKS with a matching alg & kid property.
         */
        return jwksObject.getJsonArray("keys").stream()
                .map(jwk -> jwk.asJsonObject())
                .filter(jwk -> jwk.getString("alg").equals(alg))
                .filter(jwk -> jwk.getString("kid").equals(kid))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Signing key not found with a matching alg [%s] & kid [%s] property in the filtered JWKS"));
    }

    /**
     * (5.2) Validate the access token
     *
     * @param context
     * @param accessToken
     * @param algorithm
     */
    public void validateAccessToken(OidcContext context, String accessToken, Algorithm algorithm) {
        if (context.getIdentityTokenClaims().containsKey(ACCESS_TOKEN_HASH)) {

            //Get the message digest for the JWS algorithm value used in the header(alg) of the ID Token
            MessageDigest md = getMessageDigest(algorithm);

            // Hash the octets of the ASCII representation of the access_token with the hash algorithm
            md.update(accessToken.getBytes(US_ASCII));
            byte[] hash = md.digest();

            // Take the left-most half of the hash and base64url encode it.
            byte[] leftHalf = Arrays.copyOf(hash, hash.length / 2);
            String accessTokenHash = Base64URL.encode(leftHalf).toString();

            // The value of at_hash in the ID Token MUST match the value produced
            if (!context.getIdentityTokenClaims().get(ACCESS_TOKEN_HASH).equals(accessTokenHash)) {
                throw new IllegalStateException("Invalid access token hash (at_hash) value");
            }
        }
    }

    /**
     * Get the message digest instance for the given JWS algorithm value.
     *
     * @param algorithm The JSON Web Signature (JWS) algorithm.
     *
     * @return The message digest instance
     */
    public static MessageDigest getMessageDigest(Algorithm algorithm) {
        String mdAlgorithm = "SHA-" + algorithm.getName().substring(2);

        try {
            return MessageDigest.getInstance(mdAlgorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No MessageDigest instance found with the specified algorithm : " + mdAlgorithm, ex);
        }
    }
}
