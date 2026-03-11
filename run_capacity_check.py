import csv
from collections import defaultdict
import json

def check_capacity():
    flights = []
    with open('flights.csv', 'r') as f:
        reader = csv.DictReader(f)
        for row in reader: flights.append(row)

    events = defaultdict(list)
    for f in flights:
        size = f['PlaneSize']
        isInt = f['IsInternational'] == 'true'
        arr = int(f['ArrivalMinute'])
        dep = int(f['DepartureMinute'])
        category = f"{size}_{'INT' if isInt else 'DOM'}"
        events[category].append((arr, 1))
        events[category].append((dep, -1))
    
    result = {}
    for cat, evs in events.items():
        evs.sort(key=lambda x: (x[0], x[1])) 
        current, max_sim = 0, 0
        for _, delta in evs:
            current += delta
            max_sim = max(max_sim, current)
        result[cat] = max_sim
        
    with open('capacity_results.json', 'w') as f:
        json.dump(result, f, indent=4)

check_capacity()
