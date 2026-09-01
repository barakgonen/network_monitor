#!/bin/bash

# Scan all test files and extract annotations + line counts + test method counts

MODULE_DIRS=(
  "binary-serdes"
  "rest-schema"
  "traffic-config"
  "traffic-monitor-app-core"
  "traffic-monitor-app"
  "traffic-tester-app"
  "traffic-destination-app"
  "traffic-proxy-app"
  "sample-publisher-app"
)

for module in "${MODULE_DIRS[@]}"; do
  echo "====== MODULE: $module ======"
  
  test_dir="/Users/bg/IdeaProjects/network_monitor/$module/src/test"
  
  if [ ! -d "$test_dir" ]; then
    echo "NO TEST DIRECTORY"
    echo
    continue
  fi
  
  # Find all test files
  find "$test_dir" -type f -name "*Test.java" -o -name "*IT.java" | sort | while read -r file; do
    rel_path=${file#$test_dir/}
    filename=$(basename "$file")
    lines=$(wc -l < "$file")
    test_count=$(grep -c "^\s*@Test" "$file")
    
    # Extract class-level annotations
    annotations=$(grep -E "^\s*@(SpringBootTest|WebMvcTest|DataJpaTest|RestClientTest|JdbcTest|JsonTest|ExtendWith)" "$file" | head -3 | sed 's/^\s*//' | tr '\n' '|')
    annotations=${annotations%|}
    
    echo "$filename | Lines: $lines | Tests: $test_count | Annotations: $annotations"
  done
  
  echo
done
