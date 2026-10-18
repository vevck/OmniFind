#!/usr/bin/env python3
"""Checks the Android embedder's C++ on the Mac against DigUp's reference vectors.

    python3 tools/host-check/run.py build/host-check/host_check models/ [threads]

Reads app/src/androidTest/assets/reference/{inputs.json,vectors.json}, the same files the on-phone test uses.
"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "app/src/androidTest/assets/reference"


def main() -> int:
    binary, models = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    threads = sys.argv[3] if len(sys.argv) > 3 else "4"
    inputs = json.loads((REFERENCE / "inputs.json").read_text())
    expected = json.loads((REFERENCE / "vectors.json").read_text())["vectors"]

    jobs = [f"{t['name']}\ttext\t0\t{t['text']}" for t in inputs["texts"]]
    jobs += [f"{i['name']}\timage\t{i['budget']}\t{REFERENCE / i['file']}" for i in inputs["images"]]
    jobs += [f"{a['name']}\taudio\t0\t{REFERENCE / a['file']}" for a in inputs["audio"]]
    result = subprocess.run(
        [str(binary), str(models / "embeddinggemma-2-Q8_0.gguf"), str(models / "mmproj-embeddinggemma-2-Q8_0.gguf"),
         threads],
        input="\n".join(jobs) + "\n", capture_output=True, text=True)
    sys.stderr.write(result.stderr[-2000:])
    if result.returncode != 0:
        return result.returncode

    worst = 1.0
    for line in result.stdout.splitlines():
        name, seconds, values = line.split("\t")
        vector = [float(v) for v in values.split(",")]
        cosine = sum(a * b for a, b in zip(vector, expected[name]))
        worst = min(worst, cosine)
        mark = "ok " if cosine >= inputs["agreement"] else "LOW"
        print(f"{mark} {name:14s} cosine {cosine:.5f}  {float(seconds):6.2f} s")
    print(f"worst cosine {worst:.5f} (DigUp's bar: {inputs['agreement']})")
    return 0 if worst >= inputs["agreement"] else 1


if __name__ == "__main__":
    sys.exit(main())
