
import sys
import random

# returns iteration
def xeqy(lower, upper, count, jit):
    rng = upper - lower
    results = []
    for i in range(count):
        x = max(0,jitter(lower + random.random() * rng, jit))
        y = max(0,jitter(x,jit))
        results.append((x,y))

    return results

def jitter(val, range):
    jitter = (2*range * random.random()) - range
    return val + jitter

def summ(lower, upper, count, point_limit, jit):
    rng = upper - lower
    results = []
    for i in range(count):
        x = max(0,jitter(lower + random.random() * rng,jit))
        y = max(0,min(jitter(point_limit,jit),jitter(x,jit)))
        results.append((x,y))
    return results

def main():
    JIT = float(sys.argv[5])
    results = zip(xeqy(float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3]), JIT), summ(float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), JIT))




    for x,y in results:
        print(','.join([str(a) for a in [*x,*y]]))

if __name__ == '__main__':
    main()
