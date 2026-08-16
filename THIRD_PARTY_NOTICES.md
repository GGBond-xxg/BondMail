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

## CircularRevealSwitch

BondMail's Compose theme transition is adapted from
[YenalyLiew/CircularRevealSwitch](https://github.com/YenalyLiew/CircularRevealSwitch).

MIT License

Copyright (c) 2024 YenalyLiew

Permission is hereby granted, free of charge, to any person obtaining a copy of this
software and associated documentation files (the "Software"), to deal in the
Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## MaterialKolor

BondMail uses `com.materialkolor:material-kolor` to generate a complete Material 3
tonal color scheme from the user-selected theme seed.

MaterialKolor is distributed under the MIT License. Its Material Color Utilities
implementation is derived from Google's Apache License 2.0 Material Color Utilities.
The complete upstream license texts are included in the dependency artifacts; the
Apache License 2.0 text is also included at `licenses/Apache-2.0.txt`.

## MIUIX

BondMail uses `top.yukonga.miuix.kmp:miuix` for the optional MIUIX interface
theme and native MIUIX controls.

MIUIX is distributed under the Apache License, Version 2.0. The full license
text is included at `licenses/Apache-2.0.txt`.
