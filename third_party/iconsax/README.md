Iconsax Outline and Broken, by Lusaxweb.

Source repository commit: e5dba15361f69d34d0f3f3f35408a28fe74c5727
Official SVG archive: https://raw.githubusercontent.com/lusaxweb/iconsax/e5dba15361f69d34d0f3f3f35408a28fe74c5727/static/Iconsax/Svg.zip
Archive SHA-256: b855b89705738f2ee24d98a24a9dba8d1784837fb149c334e095853591141285

Only selected glyphs are included as Android VectorDrawable XML. Compose, notifications and PiP all reference these same resources. No Flutter dependency, icon font, full archive or runtime download is included. Original path geometry/strokes are preserved; black/transparent artwork is tinted by Android/Compose. Back/alignment add separate auto-mirrored RTL variants. Broken reset uses refresh-2, whose strokes need no unsupported dash-array conversion.

Regenerate with: python3 scripts/generate-iconsax.py /path/to/Svg.zip
Validate with: python3 scripts/check-icon-assets.py

selection.json records the original SVG path and generated resource hash for each semantic key and style. The retained home folder is separate fixed artwork; see third_party/folder-artwork.

The upstream Iconsax license is a custom free-use license, not MIT. Its original terms are in LICENSE.txt and app assets/licenses/iconsax.txt.
