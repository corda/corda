ALL_UNTRUSTED_MK=$(shell find . -name '*sgx_u.mk')
ALL_TRUSTED_MK=$(shell find . -name '*sgx_t.mk')
ALL_STATIC_MK=$(shell find . -name '*sgx_t_static.mk')



.PHONY: all clean run


all clean:
	$(foreach U_MK, $(ALL_UNTRUSTED_MK), $(MAKE) -C $(shell dirname $(U_MK))  -f $(shell basename $(U_MK)) $@;)
	$(foreach T_MK, $(ALL_TRUSTED_MK), $(MAKE) -C $(shell dirname $(T_MK))    -f $(shell basename $(T_MK)) $@;)
	$(foreach U_MK, $(ALL_STATIC_MK), $(MAKE) -C $(shell dirname $(U_MK))  -f $(shell basename $(U_MK)) $@;)

run:
	$(foreach U_MK, $(ALL_UNTRUSTED_MK), $(MAKE) -C $(shell dirname $(U_MK))   -f $(shell basename $(U_MK)) $@;)

