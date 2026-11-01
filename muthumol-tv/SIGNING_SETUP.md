# Signing setup

TG_API_ID and TG_API_HASH were added by the user. Leave both in Repository secrets.

Download tools/signing-setup.html and open it in a browser. Select the existing release JKS and fill its two passwords. The page produces the three required Repository secret names and values locally, without network requests or storage. Copy each into Settings → Secrets and variables → Actions → New repository secret. Do not put signing values in Actions variables or workflow inputs.

No alias variable is needed: the workflow uses muthumol-release. Existing legacy alias variables may remain unused. The JKS must match the certificate pinned in tools/publish-update.py. Do not generate a replacement or bypass the pin. This form formats values; it does not recover or verify the private key. The workflow verifies it before publication. A missing key backup remains a blocker.

After the three signing secrets are saved, rerun the release workflow and verify the live update manifest.
