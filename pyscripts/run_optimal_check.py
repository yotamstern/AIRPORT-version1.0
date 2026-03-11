import csv

def check_optimal():
    flights = []
    with open('flights.csv', 'r') as f:
        reader = csv.DictReader(f)
        for row in reader: flights.append(row)
    
    # 30 gates: 
    # 10 SMALL_DOM, 5 SMALL_INT, 5 LARGE_DOM, 4 LARGE_INT, 6 JUMBO_INT
    # Size logic: SMALL can park anywhere? No, AirportDashboardFrame says:
    # isGateLargeEnough: 
    # SMALL -> fits anywhere
    # LARGE -> LARGE or JUMBO
    # JUMBO -> JUMBO
    # International must match EXACTLY.
    
    # Define gates
    gates = []
    # id, size, int
    # 1-10 SMALL DOM
    for i in range(10): gates.append({'id': len(gates)+1, 'size': 1, 'int': False, 'free_at': 0})
    # 11-15 SMALL INT
    for i in range(5): gates.append({'id': len(gates)+1, 'size': 1, 'int': True, 'free_at': 0})
    # 16-20 LARGE DOM
    for i in range(5): gates.append({'id': len(gates)+1, 'size': 2, 'int': False, 'free_at': 0})
    # 21-24 LARGE INT
    for i in range(4): gates.append({'id': len(gates)+1, 'size': 2, 'int': True, 'free_at': 0})
    # 25-30 JUMBO INT
    for i in range(6): gates.append({'id': len(gates)+1, 'size': 3, 'int': True, 'free_at': 0})
    
    def get_size_num(s):
        if s == 'SMALL_BODY': return 1
        if s == 'LARGE_BODY': return 2
        if s == 'JUMBO_BODY': return 3
        return 1

    for f in flights:
        f['arr'] = int(f['ArrivalMinute'])
        f['dep'] = int(f['DepartureMinute'])
        f['size_num'] = get_size_num(f['PlaneSize'])
        f['int'] = f['IsInternational'] == 'true'

    flights.sort(key=lambda x: x['arr'])

    holding = 0
    for f in flights:
        assigned = False
        for g in gates:
            # Check size
            if g['size'] >= f['size_num'] and g['int'] == f['int']:
                if g['free_at'] <= f['arr']:
                    g['free_at'] = f['dep']
                    assigned = True
                    break
        if not assigned:
            holding += 1
            
    print(f"Absolute theoretical minimum holding flights: {holding}")

check_optimal()
