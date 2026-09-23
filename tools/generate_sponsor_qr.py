"""Generate bundled donation QR codes from the single Kotlin address catalog.

Build-time only: pip install qrcode==8.2 pillow==12.3.0 zxing-cpp==2.3.0
Run from the repository root. Every image is independently decoded before writing.
The QR payload is a raw address, never a payment request or a prefilled amount.
"""
import re
from pathlib import Path

import qrcode
import zxingcpp

root = Path(__file__).resolve().parents[1]
catalog = root / "app/src/main/java/com/bond/mail/data/support/Sponsorship.kt"
output = root / "app/src/main/assets/sponsorship"
output.mkdir(parents=True, exist_ok=True)
wallets = re.findall(r'SponsorshipWallet\("([^"]+)", "([^"]+)"\)', catalog.read_text(encoding="utf-8"))
assert len(wallets) == 7, "Review wallet catalog changes before regenerating"
for network, address in wallets:
    assert re.fullmatch(r"[A-Za-z0-9_-]+", address)
    code = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=4)
    code.add_data(address, optimize=0)
    code.make(fit=True)
    image = code.make_image(fill_color="black", back_color="white").convert("RGB")
    decoded = zxingcpp.read_barcode(image)
    assert decoded is not None and decoded.text == address, network
    target = output / f"{address}.png"
    image.save(target, optimize=True)
    print(f"Verified {network}: {image.width} x {image.height}")
