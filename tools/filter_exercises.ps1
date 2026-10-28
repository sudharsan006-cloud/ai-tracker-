$raw = Get-Content 'tools/work/exercises_full.json' -Raw | ConvertFrom-Json
$allowedEquip = @('body only', 'dumbbell', 'bands', 'kettlebells', 'exercise ball', 'foam roll')
$allowedCats = @('strength', 'stretching', 'cardio', 'plyometrics')
$filtered = [System.Collections.Generic.List[PSObject]]::new()

foreach ($ex in $raw) {
    $equip = $ex.equipment
    if ($null -eq $equip -or $equip -eq '') {
        $equip = 'body only'
    }
    $cat = $ex.category
    $imgs = $ex.images
    $instr = $ex.instructions
    if ($allowedEquip -contains $equip -and $allowedCats -contains $cat -and ($imgs -and $imgs.Count -ge 1) -and ($instr -and $instr.Count -ge 1)) {
        $entry = [ordered]@{
            id = $ex.id
            name = $ex.name
            level = $ex.level
            equipment = $equip
            category = $cat
            force = $ex.force
            mechanic = $ex.mechanic
            primaryMuscles = @($ex.primaryMuscles)
            secondaryMuscles = @($ex.secondaryMuscles)
            instructions = @($ex.instructions)
            images = @($ex.images)
        }
        $filtered.Add([pscustomobject]$entry)
    }
}

Write-Host "Filtered exercises count: $($filtered.Count)"
New-Item -ItemType Directory -Force -Path 'backend/src/main/resources/seed' | Out-Null
$json = $filtered | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText('backend/src/main/resources/seed/exercises.json', $json, [System.Text.Encoding]::UTF8)
Write-Host "Written to backend/src/main/resources/seed/exercises.json"
