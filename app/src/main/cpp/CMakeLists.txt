cmake_minimum_required(VERSION 3.22.1)
project(bloomboard_abi C)

# Packaging marker only. Every screen, protocol, and input path is Kotlin.
add_library(bloomboard_abi SHARED abi_marker.c)
