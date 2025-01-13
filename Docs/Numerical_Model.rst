Numerical Model
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Emission Numerical Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Road traffic emission model
----------------------------
The emission model of the implemented road traffic is the `CNOSSOS-EU`_ model.

User can choose coefficients from the Directive 2015/996 and its amendment 2019/1010.

Rail traffic emission model
----------------------------
The emission model of the implemented rail traffic is the `CNOSSOS-EU`_ model.

Only french database, from SNCF, is implemented.


Without emission model
----------------------------
User can also add directly its own emission sound power level (LW).


Path finding algorithm
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The path finding algorithm is a rubber-band like algorithm as specified in `CNOSSOS-EU`_.

To optimize the processing time, this algorithm is taking benefit from a R-Tree spatial partioning algorithm.

.. warning::
    Rays backwards to the source or receiver are not taken into account. For example, if a receiver is located inside a U-shaped building, only diffractions on horizontal edges will be taken into account.

.. figure:: images/Numerical_Model/ray_tracing.png
    :align: center
    :width: 75%

Propagation Numerical Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The propagation model is the `CNOSSOS-EU`_ one.



.. _CNOSSOS-EU: https://circabc.europa.eu/sd/a/9566c5b9-8607-4118-8427-906dab7632e2/Directive_2015_996_EN.pdfde

