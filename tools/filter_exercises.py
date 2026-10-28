import json
import os

allowed_equipment = {"body only", "dumbbell", "bands", "kettlebells", "exercise ball", "foam roll"}
allowed_categories = {"strength", "stretching", "cardio", "plyometrics"}

input_path = os.path.join(os.path.dirname(__file__), "work", "exercises_full.json")
output_dir = os.path.join(os.path.dirname(__file__), "..", "backend", "src", "main", "resources", "seed")
os.makedirs(output_dir, exist_ok=True)
output_path = os.path.join(output_dir, "exercises.json")

with open(input_path, "r", encoding="utf-8") as f:
    exercises = json.load(f)

filtered = []
for ex in exercises:
    equip = ex.get("equipment")
    if equip is None:
        equip = "body only"
    
    category = ex.get("category")
    images = ex.get("images") or []
    instructions = ex.get("instructions") or []
    
    if equip in allowed_equipment and category in allowed_categories and len(images) >= 1 and len(instructions) >= 1:
        entry = {
            "id": ex.get("id"),
            "name": ex.get("name"),
            "level": ex.get("level"),
            "equipment": equip,
            "category": category,
            "force": ex.get("force"),
            "mechanic": ex.get("mechanic"),
            "primaryMuscles": ex.get("primaryMuscles") or [],
            "secondaryMuscles": ex.get("secondaryMuscles") or [],
            "instructions": instructions,
            "images": images
        }
        filtered.append(entry)

with open(output_path, "w", encoding="utf-8") as f:
    json.dump(filtered, f, indent=2)

print(f"Filtered exercises count: {len(filtered)}")
