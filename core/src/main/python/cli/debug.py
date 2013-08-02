#

cli_level         = False
rest_level        = False
description_level = False

def cli():
    return cli_level

def cli_set(level):
    global cli_level
    old_level = cli_level
    cli_level = level
    return old_level

def rest():
    return rest_level

def rest_set(level):
    global rest_level
    old_level = rest_level
    rest_level = level
    return old_level

def description():
    return description_level

def description_set(level):
    global description_level
    old_level = description_level
    description_level = level
    return old_level
