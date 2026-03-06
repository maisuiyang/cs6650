# Test Results and Analysis

## Overview
This directory contains all test results, performance data, and analysis from the WebSocket chat system evaluation.

## Files Structure

### Performance Data
- `WARMUP-latency.csv` - Individual message latencies (warmup phase)
- `MAIN-latency.csv` - Individual message latencies (main phase)
- `WARMUP-throughput.csv` - Throughput over time (10s buckets)
- `MAIN-throughput.csv` - Throughput over time (10s buckets)

### Console Outputs
- `part1-client-output.txt` - Basic load testing results
- `part2-console-fixed.txt` - Detailed performance analysis
- `ec2-test-results.txt` - EC2 deployment test results

### Chart Data
- `main-chart-data.txt` - Processed data for throughput visualization
- `warmup-chart-data.txt` - Processed data for warmup visualization

### Screenshots
- `screenshots/ec2-deployment-screenshot.png` - AWS EC2 console
- `screenshots/ec2-server-startup.png` - Server startup logs
- `screenshots/client-ec2-test-results.png` - Client test output
- `screenshots/throughput-chart.png` - Performance visualization

## Key Performance Results

### Local Testing
- **Throughput**: 47,501 msg/s
- **Latency p50**: 187ms
- **Success Rate**: 100%

### EC2 Testing  
- **Throughput**: 59,255 msg/s
- **Latency p50**: 2,930ms
- **Success Rate**: 100%
- **Network Impact**: Higher latency due to internet RTT

## Analysis Tools
- Use CSV files with Excel/Google Sheets for detailed analysis
- Chart data files ready for visualization tools
- Screenshots provide deployment evidence