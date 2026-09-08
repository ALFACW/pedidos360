import os
import json
from pathlib import Path

FOLDER = r"C:\Users\Diego\Desktop\cloud native"
OUT_DIR = Path(FOLDER) / "extracted_texts"
OUT_DIR.mkdir(exist_ok=True)

summary = []

# PDF extraction
try:
    from PyPDF2 import PdfReader
except Exception:
    PdfReader = None

# DOCX extraction
try:
    import docx
except Exception:
    docx = None

for entry in os.listdir(FOLDER):
    path = Path(FOLDER) / entry
    if path.is_file():
        lower = entry.lower()
        record = {"file": entry, "type": None, "pages": None, "chars": 0, "snippet": "", "status": "ok"}
        try:
            text = ""
            if lower.endswith('.pdf') and PdfReader is not None:
                record['type'] = 'pdf'
                reader = PdfReader(str(path))
                try:
                    n_pages = len(reader.pages)
                except Exception:
                    n_pages = None
                record['pages'] = n_pages
                for p in reader.pages:
                    try:
                        page_text = p.extract_text() or ''
                    except Exception:
                        page_text = ''
                    text += page_text + "\n"
            elif lower.endswith('.docx') and docx is not None:
                record['type'] = 'docx'
                doc = docx.Document(str(path))
                for para in doc.paragraphs:
                    text += para.text + "\n"
            else:
                record['type'] = Path(entry).suffix.lstrip('.')
                record['status'] = 'skipped (unsupported or missing lib)'

            if text:
                txt_name = entry.replace(' ', '_') + '.txt'
                out_path = OUT_DIR / txt_name
                with open(out_path, 'w', encoding='utf-8') as f:
                    f.write(text)
                record['chars'] = len(text)
                record['snippet'] = text[:500].replace('\n', ' ') 
        except Exception as e:
            record['status'] = f'error: {e}'
        summary.append(record)

# write summary
with open(OUT_DIR / 'summary.json', 'w', encoding='utf-8') as f:
    json.dump(summary, f, ensure_ascii=False, indent=2)

print(f"Done. Extracted texts (if any) and summary written to: {OUT_DIR}")
