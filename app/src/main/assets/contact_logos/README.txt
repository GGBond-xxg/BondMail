Contact logo SVG overrides
==========================

Place monochrome SVG files in this directory. The avatar resolver checks:

1. Full sender domain, for example: email-service.bybit.com.svg
2. Root domain, for example: bybit.com.svg
3. Brand key, for example: bybit.svg

Files here override the bundled Simple Icons files in the simpleicons subdirectory.
SVGs should include a viewBox and one or more <path d="..."> elements. Fill colors are ignored
because BondMail applies either Monet colors or the configured fixed brand colors at runtime.

Simple Icons source and usage:
https://simpleicons.org/
https://github.com/simple-icons/simple-icons
