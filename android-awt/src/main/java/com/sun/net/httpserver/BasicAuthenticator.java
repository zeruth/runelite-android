/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sun.net.httpserver;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class BasicAuthenticator extends Authenticator {
    protected final String realm;
    private final Charset charset;
    public BasicAuthenticator(String realm) { this(realm, StandardCharsets.ISO_8859_1); }
    public BasicAuthenticator(String realm, Charset charset) {
        if (realm == null || charset == null) throw new NullPointerException();
        if (realm.indexOf('"') >= 0 || realm.indexOf('\r') >= 0 || realm.indexOf('\n') >= 0) throw new IllegalArgumentException("Invalid realm");
        this.realm = realm; this.charset = charset;
    }
    public String getRealm() { return realm; }
    @Override public Result authenticate(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null) {
            challenge(exchange); return new Retry(401);
        }
        if (!authorization.regionMatches(true, 0, "Basic ", 0, 6)) return new Failure(401);
        try {
            String credentials = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), charset);
            int colon = credentials.indexOf(':');
            if (colon >= 0 && checkCredentials(credentials.substring(0, colon), credentials.substring(colon + 1)))
                return new Success(new HttpPrincipal(credentials.substring(0, colon), realm));
        } catch (IllegalArgumentException invalid) { }
        challenge(exchange); return new Failure(401);
    }
    private void challenge(HttpExchange exchange) {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"" + realm + "\"" + (charset.equals(StandardCharsets.UTF_8) ? ", charset=\"UTF-8\"" : ""));
    }
    public abstract boolean checkCredentials(String username, String password);
}
