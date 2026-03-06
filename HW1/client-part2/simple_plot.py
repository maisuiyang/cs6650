#!/usr/bin/env python3
import csv
import os

def analyze_throughput(csv_path, phase_name):
    if not os.path.exists(csv_path):
        print(f"CSV file not found: {csv_path}")
        return
    
    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        data = list(reader)
    
    if not data:
        print(f"No data in {csv_path}")
        return
    
    print(f"\n=== {phase_name} Throughput Analysis ===")
    
    for row in data:
        timestamp = int(row['timestamp'])
        count = int(row['count'])
        throughput = count / 10.0  # 10-second buckets
        
        print(f"Bucket start: {timestamp}, Messages: {count}, Throughput: {throughput:.1f} msg/s")

def create_simple_chart_data(csv_path, output_path):
    if not os.path.exists(csv_path):
        return
    
    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        data = list(reader)
    
    if not data:
        return
    
    with open(output_path, 'w') as f:
        f.write("# Throughput Chart Data\n")
        f.write("# Time(s), Throughput(msg/s)\n")
        
        start_time = int(data[0]['timestamp'])
        
        for row in data:
            timestamp = int(row['timestamp'])
            count = int(row['count'])
            seconds = (timestamp - start_time) / 1000.0
            throughput = count / 10.0
            
            f.write(f"{seconds:.1f}, {throughput:.1f}\n")

if __name__ == "__main__":
    analyze_throughput("results/WARMUP-throughput.csv", "WARMUP")
    analyze_throughput("results/MAIN-throughput.csv", "MAIN")
    
    create_simple_chart_data("results/WARMUP-throughput.csv", "results/warmup-chart-data.txt")
    create_simple_chart_data("results/MAIN-throughput.csv", "results/main-chart-data.txt")
    
    print("\nChart data files created:")
    print("- results/warmup-chart-data.txt")
    print("- results/main-chart-data.txt")
    print("You can use these files to create charts in Excel/Google Sheets")