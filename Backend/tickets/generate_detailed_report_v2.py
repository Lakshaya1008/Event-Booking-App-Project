import os
import re

def parse_java_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Basic extraction
    package_match = re.search(r'^\s*package\s+([\w\.]+);', content, re.MULTILINE)
    package_name = package_match.group(1) if package_match else "default"

    imports = re.findall(r'^\s*import\s*(?:static\s+)?([\w\.\*]+);', content, re.MULTILINE)
    
    # Class declaration
    class_def_match = re.search(r'(?:public|protected|private)?\s*(?:abstract|final|static)?\s*(class|interface|record|enum)\s+(\w+)(.*?)\{', content, re.DOTALL)
    class_type = class_def_match.group(1) if class_def_match else "unknown"
    class_name = class_def_match.group(2) if class_def_match else os.path.basename(filepath).split('.')[0]
    
    # Extract relevant code snippet (from class declaration down a few lines, usually showing fields or constructor)
    snippet = ""
    if class_def_match:
        start_idx = class_def_match.start()
        # grab the next 500 characters
        snippet_raw = content[start_idx:start_idx+600]
        lines = snippet_raw.split('\n')
        # take up to 20 lines
        snippet = "\n".join(lines[:20])
        if len(lines) > 20:
            snippet += "\n    // ... (truncated)"
            
    # Class level annotations
    annotations = re.findall(r'^\s*(@\w+(?:\(.*\))?)', content, re.MULTILINE)

    body_removed = re.sub(r'\{[^{}]*\}', ';', content)
    for _ in range(5):
        body_removed = re.sub(r'\{[^{}]*\}', ';', body_removed)
        
    method_matches = re.findall(r'^\s*(?:public|protected|private)\s+(?:<.*>\s+)?([\w\<\>\.,\[\]\?\s]+)\s+(\w+)\s*\((.*?)\)', body_removed, re.MULTILINE)
    methods = [f"{m[0].strip()} {m[1]}({m[2].strip()})" for m in method_matches if " class " not in m[0] and " interface " not in m[0]]

    field_matches = re.findall(r'^\s*(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?([\w\<\>\.,\[\]\?\s]+)\s+(\w+)\s*(?:=|;)', body_removed, re.MULTILINE)
    fields = [f"{f[0].strip()} {f[1]}" for f in field_matches if "return " not in f[0] and "class " not in f[0]]

    return {
        "file": filepath,
        "package": package_name,
        "class_name": class_name,
        "class_type": class_type,
        "annotations": [a for a in annotations if "Override" not in a and "Test" not in a][:5],
        "imports": imports,
        "fields": fields,
        "methods": methods,
        "snippet": snippet
    }

def read_config_file(filepath):
    if not os.path.exists(filepath):
        return None
    with open(filepath, 'r', encoding='utf-8') as f:
        # truncating very large files to 100 lines for the report
        lines = f.readlines()
        content = "".join(lines[:100])
        if len(lines) > 100:
            content += "\n... (truncated)"
        return content

def generate_markdown(parsed_files, config_files, root_dir):
    folders = {}
    src_dir = os.path.join(root_dir, "src")
    
    for f in parsed_files:
        rel_path = os.path.dirname(os.path.relpath(f['file'], root_dir))
        if rel_path not in folders:
            folders[rel_path] = []
        folders[rel_path].append(f)
        
    md = ["# Event Booking App - Comprehensive Codebase Report\n"]
    md.append("This document provides an exhaustive file-by-file breakdown of the entire application, detailing the purpose, dependencies, fields, behavior, and relevant code snippets for each component, along with all infrastructure configuration files.\n\n")
    
    for folder, files in sorted(folders.items()):
        md.append(f"## Folder: `{folder}`\n")
        md.append("---\n\n")
        
        for file_data in sorted(files, key=lambda x: x['class_name']):
            md.append(f"### File: `{os.path.basename(file_data['file'])}`\n")
            
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
            elif "Dto" in file_data['class_name'] or "Request" in file_data['class_name'] or "Response" in file_data['class_name']:
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
            
            internal_deps = [i for i in file_data['imports'] if i.startswith("com.event.tickets")]
            if internal_deps:
                md.append("**Internal Dependencies (What it uses):**\n")
                for d in sorted(internal_deps):
                    md.append(f"- `{d}`\n")
                md.append("\n")
                
            md.append("**File Code Structure:**\n")
            if file_data['fields']:
                md.append("- **State/Properties (Fields):**\n")
                for f in sorted(list(set(file_data['fields']))):
                    md.append(f"  - `{f}`\n")
            else:
                md.append("- **State/Properties**: None (Stateless/Interface)\n")
                
            if file_data['methods']:
                md.append("- **Behavior/Capabilities (Methods):**\n")
                for m in sorted(list(set(file_data['methods']))):
                    md.append(f"  - `{m}`\n")
            else:
                md.append("- **Behavior/Capabilities**: None explicitly visible/extracted.\n")
                
            if file_data['snippet']:
                md.append("\n**Relevant Code Snippet:**\n")
                md.append("```java\n")
                md.append(file_data['snippet'])
                md.append("\n```\n")
                
            md.append("\n<br>\n\n")

    # Appending config files
    md.append("## Infrastructure and Configuration Files\n")
    md.append("---\n\n")
    for name, path in config_files.items():
        content = read_config_file(path)
        if content:
            ext = os.path.splitext(path)[1][1:] or "yaml"
            if name == "Dockerfile": ext = "dockerfile"
            if ext == "properties" or ext == "env": ext = "properties"
            md.append(f"### `{name}`\n")
            md.append(f"Path: `{os.path.relpath(path, root_dir)}`\n")
            md.append(f"```{ext}\n")
            md.append(content)
            md.append(f"\n```\n\n<br>\n\n")
            
    return "".join(md)

if __name__ == "__main__":
    root_dir = r"c:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project - new\Backend\tickets"
    src_dir = os.path.join(root_dir, "src")
    parsed_files = []
    
    for dirpath, dirnames, filenames in os.walk(src_dir):
        for file in filenames:
            if file.endswith(".java"):
                parsed_files.append(parse_java_file(os.path.join(dirpath, file)))
                
    config_targets = {
        "Docker Compose": os.path.join(root_dir, "docker-compose.yml"),
        "Dockerfile": os.path.join(root_dir, "Dockerfile"),
        "POM (Maven)": os.path.join(root_dir, "pom.xml"),
        "Application Properties": os.path.join(root_dir, "src/main/resources/application.properties"),
        "Application Prod Properties": os.path.join(root_dir, "src/main/resources/application-prod.properties"),
    }
    
    # Add migrations
    migrations_dir = os.path.join(root_dir, "src/main/resources/db/migration")
    if os.path.exists(migrations_dir):
        for m in sorted(os.listdir(migrations_dir)):
            if m.endswith(".sql"):
                config_targets[f"Migration: {m}"] = os.path.join(migrations_dir, m)
                
    md_content = generate_markdown(parsed_files, config_targets, root_dir)
    
    out_path = os.path.join(root_dir, "docs/application-report.md")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(md_content)
    
    print(f"Generated comprehensive report with {len(parsed_files)} Java files and {len(config_targets)} config files at {out_path}!")
