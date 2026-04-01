import os
import re
import json

CONTROLLER_DIR = "src/main/java/com/event/tickets/controllers"

controllers = []

for file in os.listdir(CONTROLLER_DIR):
    if not file.endswith("Controller.java"): continue
    path = os.path.join(CONTROLLER_DIR, file)
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # Class mapping
    base_path = ""
    class_mapping_match = re.search(r'@RequestMapping\("([^"]+)"\)', content)
    if class_mapping_match:
        base_path = class_mapping_match.group(1)
        
    print(f"\n# {file} (Base: {base_path})")
    
    # Method mappings
    method_pattern = r'@(Get|Post|Put|Delete)Mapping\((?:value\s*=\s*)?((?:\{)?"[^"]+"(?:,\s*"[^"]+")*(?:\})?)?\)(?:.*?)(?:public|protected|private).*?([\w<>\[\]?]+)\s+(\w+)\s*\('
    
    matches = re.finditer(method_pattern, content, re.DOTALL)
    for match in matches:
        method = match.group(1).upper()
        route = match.group(2) if match.group(2) else '""'
        # cleanup route formatting
        route = route.replace('{', '').replace('}', '').replace('"', '').strip()
        return_type = match.group(3)
        method_name = match.group(4)
        print(f"{method} {base_path}{route} -> {return_type}")
