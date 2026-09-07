# stacks/python/packages/yarch-python/tests/test_redix_unit.py
from yarch_python.redix import Keys


def test_keys_first_segment_is_service():
    k = Keys("ysaas-scan")
    assert k.of("idem", "abc") == "ysaas-scan:idem:abc"
    assert k.of() == "ysaas-scan"


def test_keys_rejects_empty_parts():
    import pytest
    with pytest.raises(ValueError):
        Keys("ysaas-scan").of("a", "")
