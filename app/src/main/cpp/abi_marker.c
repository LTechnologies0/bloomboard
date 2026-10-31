/* Exported so each ABI split contains a real native library.
 * The application itself does not call this symbol. */
__attribute__((visibility("default"), used))
void bloomboard_abi_marker(void) {}
