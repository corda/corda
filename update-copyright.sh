for x in $(find -name *.S -or -name *.cpp -or -name *.h -or -name *.java | sort); do
  year_of_last_change=$(git log -1 --format=format:"%ai" $x | cut -c 1-4)
  copyright_years=$(grep "^/\* Copyright (c) .*, Avian Contributors$" $x | sed "s-^/\* Copyright (c) \(.*\), Avian Contributors\$-\1-")
  if [ "$copyright_years" != "" ]; then
    first_copyright_year=$(echo "$copyright_years" | sed "s/\(.*\)-.*/\1/")
    last_copyright_year=$(echo "$copyright_years" | sed "s/.*-\(.*\)/\1/")
    if [ "$last_copyright_year" != "$year_of_last_change" ]; then
      echo "$first_copyright_year-$year_of_last_change $x"
      sed -i "s:^/\* Copyright (c) .*, Avian Contributors\$:/* Copyright (c) $first_copyright_year-$year_of_last_change, Avian Contributors:" $x
    fi
  fi
done
