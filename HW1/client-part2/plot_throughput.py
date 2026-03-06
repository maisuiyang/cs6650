#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt
import sys
import os

def plot_throughput(csv_path, output_path):
    if not os.path.exists(csv_path):
        print(f"CSV file not found: {csv_path}")
        return
    
    df = pd.read_csv(csv_path)
    
    if df.empty:
        print(f"Empty CSV: {csv_path}")
        return
    
    # Convert timestamp to seconds from start
    start_time = df['timestamp'].min()
    df['seconds'] = (df['timestamp'] - start_time) / 1000.0
    
    # Calculate messages per second (10s buckets)
    df['throughput'] = df['count'] / 10.0
    
    plt.figure(figsize=(10, 6))
    plt.plot(df['seconds'], df['throughput'], marker='o', linewidth=2)
    plt.title('Throughput Over Time')
    plt.xlabel('Time (seconds)')
    plt.ylabel('Messages per Second')
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Chart saved to: {output_path}")

if __name__ == "__main__":
    # Plot both WARMUP and MAIN
    plot_throughput("results/WARMUP-throughput.csv", "results/warmup-throughput.png")
    plot_throughput("results/MAIN-throughput.csv", "results/main-throughput.png")