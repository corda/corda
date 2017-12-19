'use strict';

define([], function () {
    return {
        "30/360": {
            "day": "D30",
            "year": "Y360"
        },
        "30E/360": {
            "day": "D30E",
            "year": "Y360"
        },
        "ACT/360": {
            "day": "DActual",
            "year": "Y360"
        },
        "ACT/365 Fixed": {
            "day": "DActual",
            "year": "Y365F"
        },
        "ACT/365 L": {
            "day": "DActual",
            "year": "Y365L"
        },
        "ACT/ACT ISDA": {
            "day": "DActual",
            "year": "YISDA"
        },
        "ACT/ACT ICMA": {
            "day": "DActual",
            "year": "YICMA"
        }
    };
});