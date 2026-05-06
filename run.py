#!/usr/bin/env python3
import os
import signal
import socket
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent

SERVICES = [
    {
        "name": "config-server",
        "dir": ROOT / "services" / "flux-config-server",
        "port": 8888,
    },
    {
        "name": "discovery-service",
        "dir": ROOT / "services" / "discovery-service",
        "port": 8761,
    },
    {
        "name": "user-management-service",
        "dir": ROOT / "services" / "user-management-service",
        "port": 8081,
    },
    {
        "name": "calendar-service",
        "dir": ROOT / "services" / "calendar-service",
        "port": 8082,
    },
    {
        "name": "gateway",
        "dir": ROOT / "services" / "gateway",
        "port": 8083,
    },
    {
        "name": "notification-service",
        "dir": ROOT / "services" / "notification-service",
        "port": 8084,
    },
]

STARTUP_TIMEOUT_SECONDS = 180


class Runner:
    def __init__(self):
        self.processes = []
        self.stopping = False

    def choose_command(self, service_dir: Path):
        mvnw = service_dir / "mvnw"
        if mvnw.exists():
            return ["./mvnw", "spring-boot:run"]
        return ["mvn", "spring-boot:run"]

    def is_port_open(self, port: int) -> bool:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(1)
            return sock.connect_ex(("127.0.0.1", port)) == 0

    def wait_for_port(self, port: int, service_name: str):
        deadline = time.time() + STARTUP_TIMEOUT_SECONDS
        while time.time() < deadline:
            if self.is_port_open(port):
                print(f"[ok] {service_name} is up on port {port}")
                return
            if self.stopping:
                raise KeyboardInterrupt
            time.sleep(1)
        raise TimeoutError(
            f"Timed out waiting for {service_name} on port {port} "
            f"after {STARTUP_TIMEOUT_SECONDS}s"
        )

    def start_service(self, service):
        cmd = self.choose_command(service["dir"])
        print(f"[start] {service['name']} ({service['dir']})")
        process = subprocess.Popen(
            cmd,
            cwd=service["dir"],
            stdout=sys.stdout,
            stderr=sys.stderr,
            text=True,
            env=os.environ.copy(),
        )
        self.processes.append((service["name"], process))
        self.wait_for_port(service["port"], service["name"])

    def stop_all(self):
        if self.stopping:
            return
        self.stopping = True

        alive = [(name, p) for name, p in self.processes if p.poll() is None]
        if not alive:
            return

        print("\n[stop] Stopping all started services...")
        for _, process in alive:
            process.terminate()

        end = time.time() + 10
        while time.time() < end:
            if all(p.poll() is not None for _, p in alive):
                break
            time.sleep(0.2)

        for name, process in alive:
            if process.poll() is None:
                print(f"[kill] {name}")
                process.kill()

    def start_all(self):
        for service in SERVICES:
            self.start_service(service)

        print("\n[ready] All services started in order:")
        for service in SERVICES:
            print(f"- {service['name']} on {service['port']}")

        print("\nPress Ctrl+C to stop all services.")
        while True:
            time.sleep(1)


def main():
    runner = Runner()

    def handle_signal(sig, _frame):
        print(f"\n[signal] Received {signal.Signals(sig).name}")
        runner.stop_all()
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    try:
        runner.start_all()
    except KeyboardInterrupt:
        runner.stop_all()
        sys.exit(0)
    except Exception as exc:
        print(f"[error] {exc}", file=sys.stderr)
        runner.stop_all()
        sys.exit(1)


if __name__ == "__main__":
    main()
