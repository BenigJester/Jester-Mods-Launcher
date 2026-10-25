#pragma once

#include <fstream>
#include <sstream>
#include <string>

// Read-only, bounded proc observations. Never retain names, raw wait symbols or stacks.
namespace RootWaitDiagnostics
{
    struct Sample {
        const char *state = "unavailable";
        const char *channel = "unavailable";
        int threads = -1;
    };

    inline const char *stateCategory(char state)
    {
        switch (state) {
        case 'R': return "running";
        case 'S': return "sleeping";
        case 'D': return "uninterruptible";
        case 'T': return "stopped";
        case 't': return "tracing_stop";
        case 'Z': return "zombie";
        case 'X': return "dead";
        case 'I': return "idle";
        default: return "unavailable";
        }
    }

    inline const char *channelCategory(const std::string &channel)
    {
        if (channel.empty() || channel == "0") return "unavailable";
        if (channel.rfind("futex_", 0) == 0 || channel == "do_futex") return "futex";
        if (channel.rfind("binder_", 0) == 0) return "binder";
        if (channel == "ep_poll" || channel == "do_epoll_wait" || channel == "do_poll") return "poll";
        if (channel == "ptrace_stop" || channel == "do_jobctl_trap") return "trace_stop";
        return "other";
    }

    inline Sample parse(const std::string &status, const std::string &channel)
    {
        Sample sample;
        sample.channel = channelCategory(channel.substr(0, 128));
        std::istringstream input(status.substr(0, 16384));
        std::string line;
        while (std::getline(input, line)) {
            if (line.rfind("State:", 0) == 0) {
                std::istringstream value(line.substr(6));
                char state = 0;
                if (value >> state) sample.state = stateCategory(state);
            } else if (line.rfind("Threads:", 0) == 0) {
                std::istringstream value(line.substr(8));
                int count = 0;
                std::string extra;
                if ((value >> count) && !(value >> extra) && count > 0 && count <= 99999)
                    sample.threads = count;
            }
        }
        return sample;
    }

    inline std::string readBounded(const std::string &path, size_t limit)
    {
        std::ifstream input(path);
        std::string text(limit, '\0');
        input.read(text.data(), static_cast<std::streamsize>(limit));
        text.resize(static_cast<size_t>(input.gcount()));
        return text;
    }

    inline Sample read(int tid)
    {
        const auto prefix = "/proc/" + std::to_string(tid);
        auto channel = readBounded(prefix + "/wchan", 128);
        channel.erase(channel.find_last_not_of(" \t\r\n") + 1);
        return parse(readBounded(prefix + "/status", 16384), channel);
    }
}
