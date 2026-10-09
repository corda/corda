#!/bin/bash

#
# Helps to port the source of a CorDapp from Corda 4.14 (Hibernate 5) to Corda 4.15 (Hibernate 7). It is not a complete solution; it
# only applies the changes that can be made mechanically, and then lists everything that still needs to be reviewed by hand.
#
# Usage: migrate-cordapp-to-hibernate7.sh [--dry-run] <path to the CorDapp's source code>
#
# What it changes (in *.kt, *.java, *.gradle, *.kts and *.properties files):
#   * javax.persistence                  -> jakarta.persistence
#   * javax.persistence:javax.persistence-api:<any version>
#                                        -> jakarta.persistence:jakarta.persistence-api:3.2.0
#   * org.hibernate:hibernate-core:<version>
#                                        -> org.hibernate.orm:hibernate-core:7.4.12.Final
#   * @Type(type = "uuid-char")          -> @JdbcTypeCode(SqlTypes.VARCHAR)
#   * @Type(type = "corda-wrapper-binary") -> @JdbcTypeCode(SqlTypes.VARBINARY)
#
# See the "Upgrading to Hibernate 7" guide for the details, and for what has to be reviewed by hand.
#

dry_run=false
if [[ "$1" == "--dry-run" ]]; then
    dry_run=true
    shift
fi

root="$1"
if [[ -z "$root" || ! -d "$root" ]]; then
    echo "Usage: $0 [--dry-run] <path to the CorDapp's source code>" >&2
    exit 1
fi

files=()
while IFS= read -r -d '' f; do
    files+=("$f")
done < <(find "$root" -type f \( -name '*.kt' -o -name '*.java' -o -name '*.gradle' -o -name '*.kts' -o -name '*.properties' \) \
    -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.gradle/*' -print0)

changed=0
for f in "${files[@]}"; do
    before=$(cksum < "$f")
    tmp=$(mktemp)
    cp "$f" "$tmp"

    perl -0pi -e '
        s/javax\.persistence:javax\.persistence-api:[0-9A-Za-z.\-]+/jakarta.persistence:jakarta.persistence-api:3.2.0/g;
        s/org\.hibernate:hibernate-core:[0-9A-Za-z.\-\$\{\}_]+/org.hibernate.orm:hibernate-core:7.4.12.Final/g;
        s/javax\.persistence/jakarta.persistence/g;
        my $uuid = s/\@Type\(\s*type\s*=\s*"uuid-char"\s*\)/\@JdbcTypeCode(SqlTypes.VARCHAR)/g;
        my $bin  = s/\@Type\(\s*type\s*=\s*"corda-wrapper-binary"\s*\)/\@JdbcTypeCode(SqlTypes.VARBINARY)/g;
        if ($uuid || $bin) {
            my $semi = /import org\.hibernate\.annotations\.Type;/ ? ";" : "";
            my $imports = "import org.hibernate.annotations.JdbcTypeCode$semi\nimport org.hibernate.type.SqlTypes$semi\n";
            # Replace the old import if nothing else uses @Type, otherwise add to it.
            if (/\@Type\(/) {
                s/(import org\.hibernate\.annotations\.Type;?\n)/$1$imports/;
            } else {
                s/import org\.hibernate\.annotations\.Type;?\n/$imports/;
            }
        }
    ' "$tmp"

    if [[ "$before" != "$(cksum < "$tmp")" ]]; then
        changed=$((changed + 1))
        echo "Updated: $f"
        if [[ "$dry_run" == false ]]; then
            cp "$tmp" "$f"
        fi
    fi
    rm -f "$tmp"
done

if [[ "$dry_run" == true ]]; then
    echo "Dry run: $changed file(s) would be updated."
else
    echo "$changed file(s) updated."
fi

echo
echo "Please review the following by hand (see the \"Upgrading to Hibernate 7\" guide):"
review() {
    local pattern="$1" message="$2" exclude="${3:-^$}"
    local hits
    hits=$(grep -rnE "$pattern" "$root" --include='*.kt' --include='*.java' --include='*.gradle' --include='*.kts' 2>/dev/null | grep -v '/build/' | grep -vE "$exclude")
    if [[ -n "$hits" ]]; then
        echo
        echo "* $message"
        echo "$hits" | sed 's/^/    /'
    fi
}
review '@Type\(' "@Type(type = \"...\") no longer exists. Use @JdbcTypeCode, @JdbcType or an AttributeConverter instead."
review '\.(save|update|saveOrUpdate|delete)\(' "Session.save/update/saveOrUpdate/delete were removed. Use persist/merge/remove."
review 'org\.hibernate\.type\.|org\.hibernate\.query\.criteria\.internal|TypeDescriptor' "Hibernate's internal type and criteria classes were rewritten." 'org\.hibernate\.type\.SqlTypes'
review 'createQuery\(|createNativeQuery\(|@NamedQuery' "HQL/JPQL is validated strictly: use attribute names (not column names), and compare an association with an entity (not its id)."
review '@OrderColumn' "@OrderColumn is only supported on a java.util.List."
review 'hibernate-java8|org\.hibernate:' "Hibernate dependencies: the group is now org.hibernate.orm, and hibernate-java8 no longer exists."
exit 0
