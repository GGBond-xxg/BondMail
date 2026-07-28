# Third-party notices

## Thunderbird Android / K-9 Mail

BondMail v0.2.14.0 studied and adapted architectural ideas from the Thunderbird Android source tree supplied for comparison, especially the message WebView configuration, local-first message loading, and partial body download flow. BondMail's Compose/Room/JavaMail implementation was written for this project rather than copied as a complete module.

Thunderbird Android / K-9 Mail is distributed under the Apache License, Version 2.0.

K-9 Mail

Copyright 2008-2016, K-9 Mail Developers

Copyright 2005-2016, The Android Open Source Project

The full Apache License 2.0 text is included at `licenses/Apache-2.0.txt`.

## Microsoft Authentication Library (MSAL) for Android

BondMail v0.2.24.0 uses `com.microsoft.identity.client:msal` for Microsoft public-client OAuth sign-in and service-managed token caching.

MSAL for Android is distributed under the MIT License.

Copyright (c) Microsoft Corporation

The full MIT License text is included at `licenses/MSAL-MIT.txt`.

## Simple Icons

BondMail bundles selected monochrome brand SVGs from Simple Icons. The icons are
distributed under CC0 1.0 Universal; brand names and trademarks remain the property
of their respective owners.

The bundled CC0 text is included at
`app/src/main/assets/contact_logos/simpleicons/LICENSE.txt`.

## Google Play services authentication / Google Identity Services

BondMail v0.2.24.0 uses `com.google.android.gms:play-services-auth` for the Android Google Identity Services authorization flow. The dependency is obtained from Google's Maven repository and is governed by the applicable Android SDK / Google APIs terms. BondMail does not redistribute a Google Client Secret.
