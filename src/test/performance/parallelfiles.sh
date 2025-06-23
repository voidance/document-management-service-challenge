#!/bin/bash

# Number of parallel curl processes (can be passed as argument or defaults to 5)
parallelism=${1:-5}
total_calls=10  # Adjust this to how many total files you want to upload

# Track active background jobs
job_count=0

for i in $(seq 0 $((total_calls - 1))); do
  (
    curl -s -o /dev/null -w "Uploaded file-$i.pdf\n" -X POST http://localhost:8080/document-management/upload \
      -F "file=@file-$i.pdf;type=application/pdf" \
      -F "request={\"user\":\"aaa-$i\",\"name\":\"file-$i.pdf\",\"tags\":[\"tag-a-$i\",\"tag-b-$i\"]};type=application/json"
  ) &

  ((job_count++))

  # Wait for current batch to finish if we hit parallelism limit
  if (( job_count % parallelism == 0 )); then
    wait
  fi
done

# Wait for any remaining jobs
wait

echo "All uploads completed."