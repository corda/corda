#include <iostream>
#include <cstddef>
#include <cstring>

#include "enclave_metadata.h"
#include "elf_types.h"
#include "sgx_types.h"

static enclave_hash_result_t read_header(FILE *fp, Elf64_Ehdr *header) {
    if (0 != fseek(fp, 0, SEEK_SET)) {
        return EHR_ERROR_READ_FILE;
    }

    size_t read = fread(header, sizeof(Elf64_Ehdr), 1, fp);
    if (0 == read) {
        return EHR_ERROR_READ_ELF_HEADER;
    }

    if (header->e_ident[0] != ELFMAG0
            || header->e_ident[1] != ELFMAG1
            || header->e_ident[2] != ELFMAG2
            || header->e_ident[3] != ELFMAG3) {
        return EHR_ERROR_NOT_ELF_FORMAT;
    }

    if (header->e_ident[EI_CLASS] != ELFCLASS64) {
        return EHR_ERROR_NOT_ELF64_FORMAT;
    }

    return EHR_SUCCESS;
}

static enclave_hash_result_t find_section(FILE *fp, Elf64_Ehdr *header, const char *name, Elf64_Shdr *metadata_section) {
    fseek(fp, header->e_shoff, SEEK_SET);
    Elf64_Shdr *sections = static_cast<Elf64_Shdr*>(calloc(header->e_shnum, sizeof(Elf64_Shdr)));
    if (NULL == sections) {
        return EHR_ERROR_OUT_OF_MEMORY;
    }

    if (header->e_shnum != fread(sections, sizeof(Elf64_Shdr), header->e_shnum, fp)) {
        free(sections);
        return EHR_ERROR_READ_SECTION_HEADERS;
    }

    Elf64_Shdr *name_section = &sections[header->e_shstrndx];
    for (int i = 1; i < header->e_shnum; i++) { // Skip index 0, always empty
        char name[16];
        fseek(fp, name_section->sh_offset + sections[i].sh_name, SEEK_SET);
        fread(name, 1, 16, fp);
        if (0 == strncmp(name, ".note.sgxmeta", 16)) {
            memcpy(metadata_section, &sections[i], sizeof(Elf64_Shdr));
            free(sections);
            return EHR_SUCCESS;
        }
    }

    free(sections);
    return EHR_ERROR_NO_SGX_META_DATA_SECTION;
}

enclave_hash_result_t retrieve_enclave_hash(const char *path, uint8_t *enclave_hash) {
    FILE *fp = fopen(path, "rb");
    if (!fp) {
        return EHR_ERROR_READ_FILE;
    }

    Elf64_Ehdr header;
    enclave_hash_result_t read_header_result = read_header(fp, &header);
    if (EHR_SUCCESS != read_header_result) {
        fclose(fp);
        return read_header_result;
    }

    Elf64_Shdr section;
    enclave_hash_result_t find_section_result = find_section(fp, &header, ".note.sgxmeta", &section);
    if (EHR_SUCCESS != find_section_result) {
        fclose(fp);
        return find_section_result;
    }

    size_t metadata_offset = section.sh_offset;
    size_t metadata_align = section.sh_addralign;

    Elf64_Note note;
    fseek(fp, section.sh_offset, SEEK_SET);
    fread(&note, sizeof(Elf64_Note), 1, fp);

    if (section.sh_size != ROUND_TO(sizeof(Elf64_Note) + note.namesz + note.descsz, section.sh_addralign)) {
        fclose(fp);
        return EHR_ERROR_INVALID_SECTION_SIZE;
    }

    const char *meta_name = "sgx_metadata";
    const size_t meta_name_len = strlen(meta_name);
    char meta_name_buffer[16] = { 0 };

    fseek(fp, section.sh_offset + sizeof(Elf64_Note), SEEK_SET);
    fread(meta_name_buffer, 1, 16, fp);

    if (meta_name_len + 1 != note.namesz || 0 != strncmp(meta_name, meta_name_buffer, meta_name_len)) {
        fclose(fp);
        return EHR_ERROR_INVALID_SECTION_NAME;
    }

    size_t meta_data_offset = section.sh_offset + sizeof(Elf64_Note) + note.namesz;
    metadata_t *metadata = static_cast<metadata_t*>(malloc(sizeof(metadata_t)));
    if (NULL == metadata) {
        fclose(fp);
        return EHR_ERROR_OUT_OF_MEMORY;
    }

    fseek(fp, meta_data_offset, SEEK_SET);
    if (1 != fread(metadata, sizeof(metadata_t), 1, fp)) {
        free(metadata);
        fclose(fp);
        return EHR_ERROR_READ_META_DATA;
    }

    if (NULL != enclave_hash) {
        memcpy(enclave_hash, metadata->enclave_css.body.enclave_hash, MRE_SIZE);
    }

    free(metadata);
    fclose(fp);

    return EHR_SUCCESS;
}
