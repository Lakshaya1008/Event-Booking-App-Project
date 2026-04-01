import os
import re

def parse_java_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Basic extraction
    package_match = re.search(r'^\s*package\s+([\w\.]+);', content, re.MULTILINE)
    package_name = package_match.group(1) if package_match else "default"

    imports = re.findall(r'^\s*import\s*(?:static\s+)?([\w\.\*]+);', content, re.MULTILINE)
    
    # Simple heuristic for class/interface/enum
    class_def_match = re.search(r'(?:public|protected|private)?\s*(?:abstract|final|static)?\s*(class|interface|record|enum)\s+(\w+)(.*?)\{', content, re.DOTALL)
    class_type = class_def_match.group(1) if class_def_match else "unknown"
    class_name = class_def_match.group(2) if class_def_match else os.path.basename(filepath).split('.')[0]
    
    # Class level annotations
    annotations = re.findall(r'^\s*(@\w+(?:\(.*\))?)', content, re.MULTILINE)

    # We will just extract method names and signatures roughly
    # A rough regex for methods (public/private/protected ... returnType name(args))
    # This is imperfect for Java but captures the essence for documentation
    # Removing method bodies first to make signature matching easier
    body_removed = re.sub(r'\{[^{}]*\}', ';', content)
    # run it a few times to handle nested braces
    for _ in range(5):
        body_removed = re.sub(r'\{[^{}]*\}', ';', body_removed)
        
    method_matches = re.findall(r'^\s*(?:public|protected|private)\s+([\w\<\>\.,\s]+)\s+(\w+)\s*\((.*?)\)', body_removed, re.MULTILINE)
    methods = [f"{m[0].strip()} {m[1]}({m[2].strip()})" for m in method_matches if " class " not in m[0] and " interface " not in m[0]]

    # Fields (rough regex: access modifier + type + name ;)
    field_matches = re.findall(r'^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?([\w\<\>\.,\s]+)\s+(\w+)\s*(?:=|;)', body_removed, re.MULTILINE)
    fields = [f"{f[0].strip()} {f[1]}" for f in field_matches if "return " not in f[0]]

    return {
        "file": filepath,
        "package": package_name,
        "class_name": class_name,
        "class_type": class_type,
        "annotations": [a for a in annotations if "Override" not in a and "Test" not in a][:5], # Limit annotations to top level
        "imports": imports,
        "fields": fields,
        "methods": methods
    }

def generate_markdown(parsed_files, root_dir):
    # Group by folder
    folders = {}
    for f in parsed_files:
        rel_path = os.path.dirname(os.path.relpath(f['file'], root_dir))
        if rel_path not in folders:
            folders[rel_path] = []
        folders[rel_path].append(f)
        
    md = ["# Event Booking App - Comprehensive Codebase Report\n"]
    md.append("This document provides an exhaustive file-by-file breakdown of the entire application, detailing the purpose, dependencies, fields, and behavior of each component.\n\n")
    
    for folder, files in sorted(folders.items()):
        md.append(f"## Folder: `{folder}`\n")
        md.append("---\n\n")
        
        for file_data in sorted(files, key=lambda x: x['class_name']):
            md.append(f"### File: `{os.path.basename(file_data['file'])}`\n")
            
            # Purpose / Type
            ct = file_data['class_type']
            annotations = ", ".join(file_data['annotations'])
            
            if "Controller" in annotations or "Controller" in file_data['class_name']:
                purpose = "Handles incoming HTTP requests and routing."
            elif "Service" in annotations or "Service" in file_data['class_name']:
                purpose = "Contains core business logic and transaction management."
            elif "Repository" in annotations or "Repository" in file_data['class_name']:
                purpose = "Handles database access and projection interfaces."
            elif "Entity" in annotations or "Entity" in file_data['class_name']:
                purpose = "Database domain model representing a persistent table."
            elif "Dto" in file_data['class_name']:
                purpose = "Data Transfer Object for API request/response payload."
            elif "Exception" in file_data['class_name']:
                purpose = "Custom exception for domain-specific error handling."
            elif "Config" in file_data['class_name']:
                purpose = "Application configuration (Security, Beans, Web)."
            elif "Test" in file_data['class_name']:
                purpose = "Unit/Integration Test suite."
            else:
                purpose = f"Application {ct} component."
                
            md.append(f"**Description / What it does**: {purpose}\n")
            if annotations:
                md.append(f"**Key Annotations**: `{annotations}`\n")
                
            md.append(f"**Package**: `{file_data['package']}`\n\n")
            
            # Dependencies
            internal_deps = [i for i in file_data['imports'] if i.startswith("com.event.tickets")]
            external_deps = [i for i in file_data['imports'] if not i.startswith("com.event.tickets")]
            
            if internal_deps:
                md.append("**Internal Dependencies (What it uses):**\n")
                for d in sorted(internal_deps):
                    md.append(f"- `{d}`\n")
                md.append("\n")
                
            # Code structure
            md.append("**File Code Structure:**\n")
            
            if file_data['fields']:
                md.append("- **State/Properties (Fields):**\n")
                # Deduplicate and sort
                unique_fields = sorted(list(set(file_data['fields'])))
                for f in unique_fields:
                    md.append(f"  - `{f}`\n")
            else:
                md.append("- **State/Properties**: None (Stateless/Interface)\n")
                
            if file_data['methods']:
                md.append("- **Behavior/Capabilities (Methods):**\n")
                # Deduplicate and sort
                unique_methods = sorted(list(set(file_data['methods'])))
                for m in unique_methods:
                    md.append(f"  - `{m}`\n")
            else:
                md.append("- **Behavior/Capabilities**: None explicitly visible/extracted.\n")
                
            md.append("\n<br>\n\n")
            
    return "".join(md)

if __name__ == "__main__":
    root_dir = r"c:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project - new\Backend\tickets\src"
    parsed_files = []
    
    for dirpath, dirnames, filenames in os.walk(root_dir):
        for file in filenames:
            if file.endswith(".java"):
                parsed_files.append(parse_java_file(os.path.join(dirpath, file)))
                
    md_content = generate_markdown(parsed_files, root_dir)
    
    out_path = r"c:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project - new\Backend\tickets\docs\application-report.md"
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(md_content)
    
    print(f"Generated comprehensive report with {len(parsed_files)} files at {out_path}!")
