cmake_minimum_required(VERSION 3.22.1)
project("qunes_core_crypto")

# В реальном билде здесь линкуется liboqs
# Для сборки используется статическая библиотека для arm64/x86_64

add_library(qunes_pqc SHARED qunes_pqc.cpp)

find_library(log-lib log)

target_link_libraries(qunes_pqc ${log-lib})