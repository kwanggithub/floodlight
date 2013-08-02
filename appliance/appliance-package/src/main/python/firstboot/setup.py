import setuptools

setuptools.setup(
    name="firstboot",
    version="0.1.0",
    zip_safe=True,
    py_modules=["check", "collect", "config", "env", "firstboot", "param", "pdesc",
                "precheck", "prestart",
                "core_util", "log_util", "rest_lib", "util"],
    data_files=[("data", ["data/welcome.txt", "data/eula.txt"])],
    entry_points=dict(console_scripts=["firstboot = firstboot:main"]),
    )
