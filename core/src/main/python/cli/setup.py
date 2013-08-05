#
# Copyright (c) 2011,2012 Big Switch Networks, Inc.
# All rights reserved.
#

import setuptools
import os


def all_files_in_subdir(dir, mod, full_list):
    for f in os.listdir(dir):
        path = os.path.join(dir, f)
        if os.path.isdir(path):
            all_files_in_subdir(path, "%s.%s" % (dir, f), full_list)
        elif f.endswith('.py') and f != '__init__.py':
            full_list.append("%s.%s" % (mod, f[:-3]))
    return full_list


def all_modules_in_subdir(dir):
    mod = all_files_in_subdir(dir, '', [])
    return mod


def all_doc_in_subdirs(dir, doc_collect):
    for f in os.listdir(dir):
        path = os.path.join(dir, f)
        if os.path.isdir(path):
            all_doc_in_subdirs(path, doc_collect)
        else:
            doc_collect.append((dir, [path]))
    return doc_collect


def all_documentation(dir):
    return all_doc_in_subdirs(dir, [])


setuptools.setup(
    name="cli",
    version="0.1.0",
    zip_safe=True,
    py_modules=["cli", "rest_api", "command",
                "c_actions", "c_data_handlers",  "c_completions", "c_validations",
                "error", "debug", "feature", "bsn_constants", "term",
                "utif", "run_config", 
                "url_cache", "doc", "bigdb",
                "timesince", "fmtcnt", "bigdb_fmtcnv",
                "biglogin",
                ] + all_modules_in_subdir('desc'),
    data_files=[("data", ["data/oui.txt"])] + all_documentation("documentation"),
    entry_points=dict(console_scripts=["cli = cli:main", "biglogin = biglogin:main", ]),
    )
