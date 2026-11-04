// Executes the production writer body with deterministic permission/cache fakes.
// Android NDK compilation separately checks its real headers and ABI linkage.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');
const assert = require('node:assert/strict');
const bin = process.argv[2];
assert(bin, 'Pass the NDK toolchains/llvm/prebuilt/windows-x86_64/bin directory');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root,
  'third_party/BlackBox/Bcore/src/main/cpp/Utils/CodePatch.cpp'), 'utf8');
const cmake = fs.readFileSync(path.join(root,
  'third_party/BlackBox/Bcore/src/main/cpp/CMakeLists.txt'), 'utf8');
assert(cmake.includes('Utils/CodePatch.cpp') && cmake.includes('--wrap=CodePatch'));
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jester-codepatch-test-'));
const fixture = `
using uintptr_t = unsigned long;
using size_t = unsigned long;
using uint8_t = unsigned char;
using uint32_t = unsigned int;
enum MemoryOperationError { kMemoryOperationSuccess, kMemoryOperationError };
enum { _SC_PAGESIZE=39, PROT_READ=1, PROT_WRITE=2, PROT_EXEC=4 };
namespace std { template<class T> struct numeric_limits { static constexpr T max() { return ~T(0); } }; }
static long pageSize;
static int calls, failAt, copies, clears, bad;
static uintptr_t expectedFirst;
static size_t expectedLength;
long sysconf(int) { return pageSize; }
int mprotect(void* p, size_t n, int flags) {
  ++calls;
  if((uintptr_t)p!=expectedFirst || n!=expectedLength || flags!=(calls==1?7:5)) bad=1;
  return calls==failAt?-1:0;
}
namespace std { void* memcpy(void* dst,const void* src,size_t n) {
  ++copies;
  if(calls!=1 || ((uintptr_t)dst<expectedFirst) || (uintptr_t)dst+n>expectedFirst+expectedLength) bad=1;
  for(size_t i=0;i<n;++i) ((uint8_t*)dst)[i]=((const uint8_t*)src)[i];
  return dst;
} }
void clearCache(char*,char*) { ++clears; }
#define __builtin___clear_cache clearCache
${source.replace(/^#include[^\n]*$/gm, '')}
alignas(16384) static uint8_t bytes[65536];
static uint8_t payload[32768];
extern "C" int run() {
  for(int p : {4096,16384}) {} // replaced below; no C++ runtime needed
  return 0;
}
`.replace('for(int p : {4096,16384}) {} // replaced below; no C++ runtime needed', `
  for(int iteration=0;iteration<2;++iteration) {
    pageSize=iteration==0?4096:16384;
    for(int scenario=0;scenario<4;++scenario) {
      unsigned offset=scenario==0?4:(unsigned)pageSize-4;
      unsigned n=scenario==0?4:scenario==1?4:scenario==2?16:20000;
      uintptr_t start=(uintptr_t)bytes+offset;
      expectedFirst=start-start%(unsigned long)pageSize;
      uintptr_t end=start+n-1;
      expectedLength=end-end%(unsigned long)pageSize-expectedFirst+pageSize;
      for(int failure=0;failure<3;++failure) {
        calls=copies=clears=bad=0; failAt=failure;
        for(unsigned i=0;i<sizeof(bytes);++i) bytes[i]=0;
        for(unsigned i=0;i<n;++i) payload[i]=123;
        auto result=__wrap_CodePatch((void*)start,payload,n);
        if(bad || result!=(failure? kMemoryOperationError:kMemoryOperationSuccess)) return 1;
        if(calls!=(failure==1?1:2) || copies!=(failure==1?0:1) || clears!=copies) return 2;
        for(unsigned i=0;i<sizeof(bytes);++i)
          if(bytes[i]!=((failure!=1 && i>=offset && i<offset+n)?123:0)) return 3;
      }
    }
  }
  calls=0;
  if(__wrap_CodePatch(nullptr,payload,4)!=kMemoryOperationError || calls) return 4;
  if(__wrap_CodePatch(bytes,nullptr,4)!=kMemoryOperationError || calls) return 5;
  if(__wrap_CodePatch(bytes,payload,0)!=kMemoryOperationError || calls) return 6;
  if(__wrap_CodePatch((void*)(~uintptr_t(0)-3),payload,16)!=kMemoryOperationError || calls) return 7;
  pageSize=-1;
  if(__wrap_CodePatch(bytes,payload,4)!=kMemoryOperationError || calls) return 8;
  `);
fs.writeFileSync(path.join(dir, 'fixture.cpp'), fixture);
const exe = process.platform === 'win32' ? '.exe' : '';
cp.execFileSync(path.join(bin, 'clang++'+exe), ['--target=wasm32','-ffreestanding','-fno-builtin',
  '-nostdlib','-c',path.join(dir,'fixture.cpp'),'-o',path.join(dir,'fixture.o')], {stdio:'inherit',timeout:30000});
cp.execFileSync(path.join(bin, 'wasm-ld'+exe), ['--no-entry','--export=run',
  path.join(dir,'fixture.o'),'-o',path.join(dir,'fixture.wasm')], {stdio:'inherit',timeout:30000});
const moduleBytes = fs.readFileSync(path.join(dir,'fixture.wasm'));
const instance = new WebAssembly.Instance(new WebAssembly.Module(moduleBytes));
assert.equal(instance.exports.run(), 0, 'Production writer failed permission/boundary regression');
console.log('PASS: 4K/16K, exact boundary, cross-page/multi-page, denied write/restore, invalid input and write scope');
console.log('Isolated platform fakes only; Android memory protection and device startup remain unverified.');
// Retain the generated fixture under the printed temporary directory for reproduction.
console.log('Fixture: '+dir);
