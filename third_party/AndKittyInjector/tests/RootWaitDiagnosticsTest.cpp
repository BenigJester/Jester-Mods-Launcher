#include "RootWaitDiagnostics.hpp"
#include "KittyTrace.hpp"
#include <iostream>
#include <stdexcept>
#include <unistd.h>

static void check(bool condition) {
    if (!condition) throw std::runtime_error("Root wait diagnostic check failed");
}

__attribute__((noinline)) static void watched() { asm volatile("" ::: "memory"); }

// A forked test process only: never attaches to a game or changes its files/settings.
static void traceFixture(bool trigger) {
    const pid_t child = fork();
    check(child >= 0);
    if (child == 0) {
        for (;;) {
            if (trigger) watched();
            usleep(1000);
        }
    }
    try {
        KittyTraceMgr trace(child);
        check(trace.seize(PTRACE_O_EXITKILL));
        check(trace.stop());
        const auto result = trace.setHardBreakpointsAndWait({reinterpret_cast<uintptr_t>(&watched)},
            KT_HW_BP_EXECUTE, KT_HW_BP_SIZE_EXEC, 0,
            [](uintptr_t, user_regs_struct) { return true; }, 250);
        check(result == (trigger ? KT_BP_SUCCESS : KT_BP_TIMEOUT));
        check(trace.detach());
    } catch (...) {
        kill(child, SIGKILL);
        waitpid(child, nullptr, 0);
        throw;
    }
    kill(child, SIGKILL);
    waitpid(child, nullptr, 0);
}

int main() {
    try {
        const auto sample = RootWaitDiagnostics::parse("Name:\tprivate\nState:\tS (sleeping)\nThreads:\t37\n", "futex_wait_queue");
        check(std::string(sample.state) == "sleeping" && sample.threads == 37 && std::string(sample.channel) == "futex");
        for (char state : std::string("RSDTtZXI")) check(std::string(RootWaitDiagnostics::stateCategory(state)) != "unavailable");
        check(std::string(RootWaitDiagnostics::stateCategory('?')) == "unavailable");
        check(std::string(RootWaitDiagnostics::channelCategory("binder_thread_read")) == "binder");
        check(std::string(RootWaitDiagnostics::channelCategory("ep_poll")) == "poll");
        check(std::string(RootWaitDiagnostics::channelCategory("ptrace_stop")) == "trace_stop");
        check(std::string(RootWaitDiagnostics::channelCategory("0")) == "unavailable");
        check(std::string(RootWaitDiagnostics::channelCategory("private_unknown_symbol")) == "other");
        for (const char *count : {"0", "-1", "100000", "999999999999999999999999", "3 private"})
            check(RootWaitDiagnostics::parse(std::string("Threads: ") + count, "").threads == -1);
        check(RootWaitDiagnostics::parse(std::string(16384, 'x') + "\nThreads: 42", "").threads == -1);
        check(RootWaitDiagnostics::read(-1).threads == -1);
        check(RootWaitDiagnostics::read(getpid()).threads > 0);
        traceFixture(false);
        traceFixture(true);
        std::cout << "PASS: bounded parser, live proc read, timeout cleanup and successful breakpoint\n";
        return 0;
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
