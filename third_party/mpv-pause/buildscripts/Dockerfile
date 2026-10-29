FROM ubuntu:24.04
RUN --mount=type=secret,id=proxy_ca,required=true \
    apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca update && \
    DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca install -y --no-install-recommends \
      build-essential python3 python3-pip python3-jinja2 meson ninja-build cmake pkg-config \
      autoconf automake libtool libtool-bin gettext gperf bison flex texinfo help2man git xz-utils patch ca-certificates && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /build
