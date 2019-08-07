#!/bin/sh

# Given a copy of the OS repo in the subdirectory "os" of the enterprise repo,
# this script produces a report showing differences in source files both repos
# have in common, broken down by source directory.

# Where we store working files
WORKING_DIR=working

# Where we store the report
REPORT_DIR=$WORKING_DIR/report
REPORT=$REPORT_DIR/index.html

## Clear out old working files and initialise working directories
rm -rf $WORKING_DIR
mkdir -p $WORKING_DIR
mkdir -p $REPORT_DIR

## Directory-level analysis

# Locate all directories containing "src", in both repos
OS_SRC_DIRS=$WORKING_DIR/os_src_dirs
ENT_SRC_DIRS=$WORKING_DIR/ent_src_dirs

find os -name src -exec echo {} \; | sed "s/os\///" | sort > $OS_SRC_DIRS
find . -name src -not -path "./os/*" -exec echo {} \; | sed "s/\.\///" | sort > $ENT_SRC_DIRS

# List out those directories which appear only in one repo, and those which appear in both.
OS_ONLY_DIRS=$WORKING_DIR/os_only_dirs
ENT_ONLY_DIRS=$WORKING_DIR/ent_only_dirs
BOTH_DIRS=$WORKING_DIR/both_dirs

diff --new-line-format="" --unchanged-line-format="" $OS_SRC_DIRS $ENT_SRC_DIRS > $OS_ONLY_DIRS
diff --new-line-format="" --unchanged-line-format="" $ENT_SRC_DIRS $OS_SRC_DIRS > $ENT_ONLY_DIRS
diff --old-line-format="" --new-line-format="" --unchanged-line-format="%L" $OS_SRC_DIRS $ENT_SRC_DIRS > $BOTH_DIRS

## File-level analysis

SRC_ROOTS=$WORKING_DIR/src_roots
mkdir -p $SRC_ROOTS

# Diff each pair of directories
index=0
while read -r src_root_name; do
  src_root="$SRC_ROOTS/$index"
  mkdir -p $src_root

  os_dir="os/${src_root_name}"
  diff -qr $os_dir $src_root_name >> "$src_root/summary.diff"
  index=$((index + 1))
done < $BOTH_DIRS

# Extract a list of the files which have differences, in each source root.
for src_root in $SRC_ROOTS/*; do
  # Find the files which appear in both source trees and have differences
  grep differ "$src_root/summary.diff" | cut -d' ' -f2 | sed "s/os\///" > "$src_root/different_files"
done

# Obtain diffs and file and line counts for each source root
index=0
while read -r src_root_name; do
  lineCount=0
  src_root=$SRC_ROOTS/$index
  diffs=$src_root/diffs
  mkdir -p $diffs

  # For each file where there is a difference, write a diff file and update the line count.
  file_index=0
  while read -r different_file; do
    diff_file="$diffs/${file_index}.diff"
    diff "os/$different_file" "$different_file" > $diff_file
    diffCount=`wc -l < $diff_file`
    lineCount=$((lineCount + diffCount))
    file_index=$((file_index + 1))
  done < "$src_root/different_files"

  echo $lineCount > $src_root/line_count
  index=$((index + 1))
done < $BOTH_DIRS

## Report generation

report() {
  echo "$1" >> $REPORT
}

reportList() {
  echo "<ul>" >> $REPORT
  while read -r line; do
    echo "<li>${line}</li>" >> $REPORT
  done < $1
  echo "</ul>" >> $REPORT
}

report "<html><head><title>OS / ENT Diffs</title></head><body>"

report "<h1>Source directory listings</h1>"
report "<h2>Directories only in OS</h2>"
reportList $OS_ONLY_DIRS

report "<h2>Directories only in ENT</h2>"
reportList $ENT_ONLY_DIRS

report "<h2>Directories in both trees with file differences</h2>"
report "<ul>"

# Create a list with HTML links to the actual diffs (which will appear later in the report), and calculate total file and line counts.
index=0
totalFileCount=0
totalLineCount=0
while read -r src_root_name; do
  src_root=$SRC_ROOTS/$index
  fileCount=`wc -l < $src_root/different_files`
  lineCount=`cat $src_root/line_count`

  # Skip source directories with no differences.
  if [ $fileCount -gt 0 ]; then
    # Add the source root to the list.
    report "<li><a href=\"#root_${index}\">$src_root_name ($lineCount lines in $fileCount files)</a></li>"
  fi

  index=$((index + 1))
  totalFileCount=$((totalFileCount + fileCount))
  totalLineCount=$((totalLineCount + lineCount))
done < $BOTH_DIRS
report "</ul>"


report "<h1>Statistics</h1>"
report "<p><strong>Total file count</strong>: $totalFileCount</p>"
report "<p><strong>Total line count</strong>: $totalLineCount</p>"

report "<h1>Detailed diffs</h1>"

# Generate diff report for each source directory
index=0
while read -r src_root_name; do
  src_root=$SRC_ROOTS/$index
  diffs=$src_root/diffs

  lineCount=`cat $src_root/line_count`
  fileCount=`wc -l < $src_root/different_files`

  # Skip source directories with no differences.
  if [ $fileCount -eq 0 ]; then
    index=$((index + 1))
    continue;
  fi
  
  title="File diffs for ${src_root_name} ($lineCount lines across $fileCount files)"
  report "<h2><a name="root_${index}">$title</h2>"

  # Format each diff and add it to the report.
  file_index=0
  while read -r different_file; do
    diff_file="$diffs/${file_index}.diff"

    report "<h3>$different_file</h3>"
    report "<pre>"
    cat "$diff_file" | sed "s/\&/\&amp;/g" | sed "s/</\&lt;/g" | sed "s/>/\&gt;/g" >> $REPORT
    report "</pre>"
    file_index=$((file_index + 1))
  done < "$src_root/different_files"

  index=$((index + 1))
done < $BOTH_DIRS

report "</body></html>"

zip diffs.zip -q $REPORT 
echo "##teamcity[buildStatisticValue key='diffLineCount' value='$totalLineCount']"
echo "##teamcity[buildStatisticValue key='diffFileCount' value='$totalFileCount']"
